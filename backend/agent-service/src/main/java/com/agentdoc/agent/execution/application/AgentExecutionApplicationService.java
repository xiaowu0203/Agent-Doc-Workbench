package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.enums.AgentExecutionStatus;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.agent.execution.runtime.AgentExecutionRuntime;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.service.AgentService;
import com.agentdoc.agent.service.ModelService;
import com.agentdoc.agent.service.PromptService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.constant.A2aMetadataConstant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.agentdoc.agent.constant.AgentConstant.MAX_ERROR_MESSAGE_LENGTH;

/**
 * Agent执行应用服务，业务层入口
 * <p>
 * 由 {@link com.agentdoc.agent.a2a.executor.WorkbenchAgentExecutor} 调用，承接A2A协议下发的Agent执行与取消请求；
 * 职责：参数解析、幂等校验、加载Agent/模型、数据库落库、调用运行时执行大模型、
 * 捕获各类执行异常、状态流转、通过{@link AgentEmitter}向A2A协议层输出事件。
 * </p>
 * <p>幂等逻辑：根据workbenchTaskId判断任务是否已经存在，已存在直接回放已有状态，不重复执行。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionApplicationService {

    private final AgentExecutionMapper executionMapper;
    private final AgentService agentService;
    private final ModelService modelService;
    private final PromptService promptService;

    /** Agent实际运行时，封装LLM调用、MCP工具调用、文档协作业务逻辑 */
    private final AgentExecutionRuntime runtime;
    /** JSON对象转换，用于解析A2A消息内的自定义DataPart */
    private final ObjectMapper objectMapper;

    /**
     * 执行Agent任务主流程
     * @param context A2A请求上下文，携带taskId、contextId、原始消息、用户输入
     * @param emitter A2A事件发射器，向外推送：开始执行、产出产物、完成、失败、取消等事件
     */
    public void execute(RequestContext context, AgentEmitter emitter) {
        // 从A2A消息的DataPart解析出业务DTO，携带workbenchTaskId、agentId等业务参数
        AgentTaskInputDTO input = extractInput(context.getMessage());
        // 幂等：按workbench业务任务id查询是否已有执行记录
        AgentExecutionEntity existing = findByWorkbenchTaskId(input.workbenchTaskId());
        if (existing != null) {
            // 任务已存在，触发回调task-service 进行任务状态同步
            emitExisting(existing, emitter);
            return;
        }

        // 校验Agent配置，校验Agent是否启用
        AgentEntity agent = agentService.require(input.agentId());
        if (!AgentStatus.ENABLED.matches(agent.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已禁用");
        }
        // 获取启用的模型配置
        ModelEntity model = modelService.requireEnabled(agent.getModelId());

        // 用户原始输入指令
        String instruction = context.getUserInput();
        // 拼装System系统提示词
        String systemPrompt = promptService.systemPrompt(agent.getSystemPrompt());

        // 构建执行记录实体，写入数据库（初始状态）
        AgentExecutionEntity execution = AgentExecutionConvertor.toEntity(
                context.getTaskId(), context.getContextId(), input, agent, model,
                systemPrompt, promptService.hash(systemPrompt, instruction), objectMapper);
        executionMapper.insert(execution);

        // 触发回调task-service，任务开始执行
        emitter.startWork();
        // 更新数据库状态为WORKING执行中
        AgentExecutionConvertor.markWorking(execution);
        executionMapper.updateById(execution);
        try {
            // 调用运行时执行业务逻辑；传入取消回调，运行时内部可轮询判断是否被取消
            boolean streaming = context.getCallContext() != null
                    && Boolean.TRUE.equals(context.getCallContext().getState()
                    .get(A2aMetadataConstant.STREAMING_REQUEST_STATE));
            AgentRuntimeResult result;
            if (streaming) {
                result = runtime.execute(agent, model, instruction, input,
                        () -> isCancelRequested(execution.getId()), emitter::sendMessage);
            } else {
                result = runtime.execute(agent, model, instruction, input,
                        () -> isCancelRequested(execution.getId()));
            }
            // 执行成功，回填结果，更新数据库状态为COMPLETED
            AgentExecutionConvertor.complete(execution, result);
            executionMapper.updateById(execution);
            // 触发回调task-service，保存正式结果和元数据（无关状态），跟下面的complete区分开，一个处理数据，一个处理状态
            emitter.addArtifact(
                    // 内容
                    List.of(new TextPart(result.summary())),
                    //产物类型
                    A2aMetadataConstant.EXECUTION_SUMMARY_ARTIFACT,
                    // 展示名称
                    "Execution Summary",
                    // token元数据
                    tokenMetadata(execution, result));
            // 触发回调task-service，任务已经完成了
            emitter.complete(agentMessage(result.summary()));
        } catch (AgentExecutionCanceledException exception) {
            // 捕获主动取消异常，状态置为CANCELED
            AgentExecutionConvertor.cancel(execution);
            executionMapper.updateById(execution);
            // 触发回调task-service，任务取消了
            emitter.cancel(agentMessage(exception.getMessage()));
        } catch (RuntimeException exception) {
            // 运行时异常：模型报错、工具异常等，状态置为FAILED
            String errorMessage = safeMessage(exception);
            AgentExecutionConvertor.fail(execution, errorMessage);
            executionMapper.updateById(execution);
            // 触发回调task-service，任务失败了
            emitter.fail(agentMessage(errorMessage));
        }
    }

    /**
     * 取消Agent任务
     * @param context A2A请求上下文
     * @param emitter A2A事件发射器
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(RequestContext context, AgentEmitter emitter) {
        // 通过A2A taskId查询执行记录
        AgentExecutionEntity execution = findByA2aTaskId(context.getTaskId());
        if (execution == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 执行不存在");
        }
        // 修改状态为已取消，写库
        AgentExecutionConvertor.cancel(execution);
        executionMapper.updateById(execution);
        // 触发回调task-service，任务开始执行
        emitter.cancel(agentMessage("任务已取消"));
    }

    /**
     * 从A2A Message中解析自定义DataPart，得到业务入参DTO
     * @param message A2A原始消息
     * @return 业务输入DTO
     */
    private AgentTaskInputDTO extractInput(Message message) {
        return message.parts().stream()
                .filter(DataPart.class::isInstance)
                .map(DataPart.class::cast)
                .map(DataPart::data)
                .map(value -> objectMapper.convertValue(value, AgentTaskInputDTO.class))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "A2A 消息缺少 Workbench DataPart"));
    }

    /**
     * 根据workbench业务任务ID查询Agent执行记录（幂等判断）
     * @param taskId workbench侧任务id
     * @return 执行实体 or null
     */
    private AgentExecutionEntity findByWorkbenchTaskId(Long taskId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<AgentExecutionEntity>()
                .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));
    }

    /**
     * 根据A2A协议taskId查询Agent执行记录，用于cancel取消流程
     * @param taskId A2A任务ID
     * @return 执行实体 or null
     */
    private AgentExecutionEntity findByA2aTaskId(String taskId) {
        return executionMapper.selectOne(new LambdaQueryWrapper<AgentExecutionEntity>()
                .eq(AgentExecutionEntity::getA2aTaskId, taskId));
    }

    /**
     * 轮询回调：查询数据库标记，判断是否收到取消请求
     * @param executionId agent_execution主键ID
     * @return true=请求取消，false=继续执行
     */
    private boolean isCancelRequested(Long executionId) {
        AgentExecutionEntity current = executionMapper.selectById(executionId);
        return current != null && Boolean.TRUE.equals(current.getCancelRequested());
    }

    /**
     * 已有任务：根据数据库里的状态回放对应的A2A事件，实现幂等重入
     * @param execution 已存在的执行记录
     * @param emitter A2A事件发射器
     */
    private void emitExisting(AgentExecutionEntity execution, AgentEmitter emitter) {
        switch (AgentExecutionStatus.valueOf(execution.getStatus())) {
            case COMPLETED -> emitter.complete(agentMessage(execution.getResultSummary()));
            case FAILED -> emitter.fail(agentMessage(execution.getErrorMessage()));
            case CANCELED -> emitter.cancel(agentMessage("任务已取消"));
            default -> emitter.startWork(agentMessage("任务已在执行"));
        }
    }

    /**
     * 构造Agent角色的A2A消息对象
     * @param text 消息文本内容
     * @return A2A Message
     */
    private Message agentMessage(String text) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(new TextPart(text == null ? "" : text))
                .messageId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * 组装Artifact元数据：token消耗、执行ID、prompt哈希等，随产物返回A2A上层
     * @param execution 执行记录实体
     * @param result runtime返回的运行结果
     * @return 元数据Map
     */
    private Map<String, Object> tokenMetadata(AgentExecutionEntity execution, AgentRuntimeResult result) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(A2aMetadataConstant.INPUT_TOKENS, result.tokenUsage().input().value());
        metadata.put(A2aMetadataConstant.INPUT_TOKENS_ESTIMATED,
                result.tokenUsage().input().estimated());
        metadata.put(A2aMetadataConstant.CACHED_INPUT_TOKENS, result.tokenUsage().cachedInput().value());
        metadata.put(A2aMetadataConstant.CACHED_INPUT_TOKENS_ESTIMATED,
                result.tokenUsage().cachedInput().estimated());
        metadata.put(A2aMetadataConstant.OUTPUT_TOKENS, result.tokenUsage().output().value());
        metadata.put(A2aMetadataConstant.OUTPUT_TOKENS_ESTIMATED,
                result.tokenUsage().output().estimated());
        metadata.put(A2aMetadataConstant.AGENT_EXECUTION_ID, execution.getId());
        metadata.put(A2aMetadataConstant.PROMPT_HASH, execution.getPromptHash());
        return metadata;
    }

    /**
     * 安全截取异常信息，防止错误消息过长
     * @param exception 运行时异常
     * @return 截断后的异常文本
     */
    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH));
    }
}
