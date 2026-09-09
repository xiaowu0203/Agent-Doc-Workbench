package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.TaskOutputType;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.TaskExecutionDetailVO;
import com.agentdoc.task.pojo.vo.TaskOutputVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聚合任务基础信息、Agent 执行审计和业务产物的只读查询服务。
 * <p>用于前端展示任务执行详情，聚合任务本体、Agent执行审计日志、任务产出产物（变更请求/草稿文档）；
 * 任务权限校验委托给TaskService，本服务只做数据聚合组装。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskExecutionQueryService {

    private final TaskService taskService;
    private final AgentFeign agentFeign;
    private final ChangeRequestMapper changeRequestMapper;

    /**
     * 查询前端任务执行详情。任务读取权限由 {@link TaskService#detail(Long)} 统一校验。
     * <p>逻辑：获取任务基础信息 → 获取Agent执行审计；
     * 若任务本身无token用量，使用Agent审计的输入+输出token进行回填；
     * 优先使用审计返回的Agent名称，兜底查询Agent基础信息；
     * 解析任务产出产物，组装完整详情VO返回。</p>
     * @param taskId 任务 ID
     * @return 聚合执行详情
     */
    public TaskExecutionDetailVO detail(Long taskId) {
        // 获取任务基础信息，内部已做权限校验
        TaskVO task = taskService.detail(taskId);
        // 获取Agent执行审计信息
        AgentExecutionAuditVO execution = executionAudit(taskId, task.spaceId());

        // 任务未记录token用量时，从Agent执行审计计算总token，并更新估算标记
        if (execution != null && task.tokensUsed() == null
                && execution.inputTokens() != null && execution.outputTokens() != null) {
            task = task.withTokenUsage(execution.inputTokens() + execution.outputTokens(),
                    Boolean.TRUE.equals(task.tokensEstimated())
                            || Boolean.TRUE.equals(execution.inputTokensEstimated())
                            || Boolean.TRUE.equals(execution.outputTokensEstimated()));
        }

        // Agent名称：优先使用审计返回的名称，兜底调用Agent接口查询
        String agentName = execution == null || execution.agentName() == null
                ? currentAgentName(task.agentId()) : execution.agentName();

        return new TaskExecutionDetailVO(task, agentName,
                Boolean.TRUE.equals(task.tokensEstimated()), execution, output(task, execution));
    }

    /**
     * 远程调用Agent服务，获取任务对应的Agent执行审计记录
     * @param taskId 任务ID
     * @param spaceId 空间ID
     * @return Agent执行审计VO，调用失败抛出业务异常
     */
    private AgentExecutionAuditVO executionAudit(Long taskId, Long spaceId) {
        Result<AgentExecutionAuditVO> result = agentFeign.getExecutionAudit(taskId, spaceId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "Agent 执行审计查询失败" : result.message());
        }
        return result.data();
    }

    /**
     * 兜底查询Agent名称，当执行审计没有返回Agent名称时使用
     * @param agentId AgentID
     * @return Agent名称，查询不到返回null
     */
    private String currentAgentName(Long agentId) {
        Result<List<AgentRefVO>> result = agentFeign.queryAgentRefs(new AgentBatchQueryDTO(List.of(agentId)));
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "Agent 信息查询失败" : result.message());
        }
        return result.data() == null || result.data().isEmpty() ? null : result.data().getFirst().name();
    }

    /**
     * 解析任务的业务输出产物
     * <p>优先级：
     * 1. 存在该任务生成的变更请求 → 返回变更请求产物
     * 2. 文档类型为草稿，且存在结果摘要 或者 执行调用过草稿写入工具且成功 → 返回草稿文档产物
     * 3. 无产出返回null</p>
     * @param task 任务VO
     * @param execution Agent执行审计信息
     * @return 任务输出产物VO，无产出返回null
     */
    private TaskOutputVO output(TaskVO task, AgentExecutionAuditVO execution) {
        // 查询该任务最新生成的变更请求（按创建时间倒序取第一条）
        ChangeRequestEntity request = changeRequestMapper.selectOne(
                new LambdaQueryWrapper<ChangeRequestEntity>()
                        .eq(ChangeRequestEntity::getSourceTaskId, task.id())
                        .orderByDesc(ChangeRequestEntity::getCreatedAt)
                        .last("LIMIT 1"));

        // 情况1：任务产生过变更请求，输出变更请求产物
        if (request != null) {
            return new TaskOutputVO(TaskOutputType.CHANGE_REQUEST, request.getId(),
                    ChangeRequestStatus.fromCode(request.getStatus()).name(), request.getDocumentId());
        }

        // 判断是否成功执行草稿写入工具
        boolean draftWriteSucceeded = execution != null && execution.toolCalls().stream()
                .anyMatch(call -> "workbench_apply_draft_changes".equals(call.toolName())
                        && "SUCCEEDED".equals(call.status()));

        // 情况2：文档为草稿类型，并且有结果摘要 或者 草稿写入工具执行成功，输出草稿文档产物
        if (task.documentType() == DocType.DRAFT && (task.resultSummary() != null || draftWriteSucceeded)) {
            return new TaskOutputVO(TaskOutputType.DRAFT_DOCUMENT, task.documentId(), null,
                    task.documentId());
        }

        // 无业务产出
        return null;
    }
}