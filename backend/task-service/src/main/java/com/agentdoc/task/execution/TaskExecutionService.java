package com.agentdoc.task.execution;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.config.RabbitTaskConfiguration;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.AgentEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.runtime.AgentExecutionContext;
import com.agentdoc.task.runtime.AgentExecutionResult;
import com.agentdoc.task.runtime.AgentRuntime;
import com.agentdoc.task.security.McpConfigCryptoService;
import com.agentdoc.task.service.AgentService;
import com.agentdoc.task.service.AuditLogService;
import com.agentdoc.task.service.ChangeRequestService;
import com.agentdoc.task.service.TaskMessagePublisher;
import com.agentdoc.task.service.TaskService;
import com.agentdoc.task.service.TokenUsageService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * RabbitMQ 任务消费者和单 Agent 执行编排。
 * <p>
 * 监听RabbitMQ任务队列，消费任务消息，驱动Agent完整执行流程。
 * 核心职责：消息消费防重复、分布式锁控、任务状态流转、Agent运行时调用、文档变更提交、异常重试、失败处理、审计日志。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final AgentService agentService;
    private final DocumentFeign documentFeign;
    private final ChangeRequestService changeRequestService;
    private final AgentRuntime agentRuntime;
    private final TaskMessagePublisher messagePublisher;
    private final RedisUtils redisUtils;
    private final McpConfigCryptoService cryptoService;
    private final TaskCapabilityVerifier capabilityVerifier;
    private final TokenUsageService tokenUsageService;
    private final AuditLogService auditLogService;

    /**
     * RabbitMQ任务队列消费入口
     * <p>
     * 消息模式：手动ACK；处理任务执行全流程；
     * Redis分布式锁控制空间任务串行；数据库乐观锁防止重复消费。
     * </p>
     * @param taskId 待执行任务ID
     * @param message Rabbit原始消息对象
     * @param channel MQ通道，用于手动ack/nack/reject
     * @throws IOException MQ IO异常
     */
    @RabbitListener(queues = RabbitTaskConfiguration.QUEUE)
    public void consume(Long taskId, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        TaskEntity task = taskService.require(taskId);

        // 任务已经不是待执行状态，直接确认丢弃消息，不再处理
        if (TaskStatus.fromCode(task.getStatus()) != TaskStatus.PENDING) {
            channel.basicAck(tag, false);
            return;
        }

        // 空间维度分布式锁：同一个空间同一时间只允许一个任务执行，避免文档并发写冲突
        String lockKey = RedisKeyConstants.TASK_SPACE_LOCK_PREFIX + task.getSpaceId();
        if (!redisUtils.setIfAbsent(lockKey, taskId,
                Duration.ofMinutes(TaskConstant.TASK_LOCK_TIMEOUT_MINUTES))) {
            // 获取锁失败，Nack重回队列稍后重试
            channel.basicNack(tag, false, true);
            return;
        }

        try {
            // 数据库乐观锁：仅PENDING状态才流转为RUNNING；防止重复消费重复启动任务
            if (!markRunning(taskId)) {
                channel.basicAck(tag, false);
                return;
            }
            // 审计日志：任务开始执行
            auditLogService.recordAgent(taskService.require(taskId).getSpaceId(), taskId,
                    taskService.require(taskId).getAgentId(), AuditAction.TASK_STARTED,
                    AuditTargetType.TASK, taskId, null);
            // 执行Agent业务逻辑
            execute(taskService.require(taskId));
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            // 执行发生异常，进入失败&重试处理分支
            handleFailure(taskId, ex, channel, tag);
        } finally {
            // 无论成功失败，必须释放空间分布式锁
            redisUtils.delete(lockKey);
        }
    }

    /**
     * Agent任务核心执行逻辑
     * <p>
     * 1.解密任务能力令牌，校验令牌，放入线程上下文；
     * 2.Feign获取文档执行上下文，读取文档起始片段；
     * 3.调用AgentRuntime完成大模型推理；
     * 4.记录Token消耗，预算耗尽直接终止；
     * 5.有变更：正式文档走ChangeRequest，草稿直接apply变更；无变更直接完成；
     * 6.finally清理ThreadLocal令牌，防止线程池内存泄露。
     * </p>
     * @param task 待执行任务实体
     */
    private void execute(TaskEntity task) {
        AgentEntity agent = agentService.require(task.getAgentId());
        // 解密获取任务原始能力令牌
        String token = cryptoService.decrypt(task.getCapabilityToken());
        // token校验
        capabilityVerifier.verify(token);
        // 将任务令牌放入当前线程上下文，供下游Feign拦截器自动注入请求头
        TaskCapabilityContext.set(token);
        try {
            // 获取文档执行上下文信息
            DocumentExecutionContextVO document = requireData(documentFeign.getExecutionContext(task.getDocumentId()));
            // 读取文档初始片段，作为Agent输入上下文
            DocumentFragmentVO fragment = requireData(documentFeign.readFragment(
                    task.getDocumentId(), TaskConstant.INITIAL_FRAGMENT_START, TaskConstant.INITIAL_FRAGMENT_LENGTH));

            /**
             * 调用Agent运行时，执行大模型推理
             */
            AgentExecutionResult result = agentRuntime.execute(agent,
                    new AgentExecutionContext(task.getId(), task.getAgentId(), task.getDocumentId(),
                            task.getInstruction(), fragment.content(), fragment.start(), fragment.totalLength()));
            // 记录token消耗；返回false代表token预算耗尽，任务已被终止，直接返回不再继续
            if (!tokenUsageService.record(task, agent, result)) {
                // 审计日志
                auditLogService.recordAgent(task.getSpaceId(), task.getId(), task.getAgentId(),
                        AuditAction.TASK_BUDGET_TERMINATED, AuditTargetType.TASK,
                        task.getId(), "Token 预算已用尽");
                return;
            }

            // Agent没有产出任何变更，直接标记任务完成
            if (result.changes() == null || result.changes().isEmpty()) {
                // 标记任务完成
                complete(task, result.summary());
                return;
            }

            // 根据文档类型分支处理变更落地
            if (document.docType() == DocType.FORMAL.getCode()) {
                // 正式文档：生成变更请求，等待人工审阅
                changeRequestService.submitFromAgent(task, result, document.version());
            } else {
                // 草稿文档：直接应用Agent产生的修改
                requireData(documentFeign.applyDraftAgentChanges(new MergeRequestDTO(
                        task.getDocumentId(), document.version(), result.changes(), result.summary())));
            }

            // 标记任务完成
            complete(task, result.summary());
            // 审计日志
            auditLogService.recordAgent(task.getSpaceId(), task.getId(), task.getAgentId(),
                    AuditAction.TASK_COMPLETED, AuditTargetType.TASK, task.getId(), result.summary());
        } finally {
            // 强制清除线程上下文中的能力令牌，复用线程池避免令牌残留泄露
            TaskCapabilityContext.clear();
        }
    }

    /**
     * 乐观锁将任务状态由PENDING更新为RUNNING，并赋值开始时间
     * @param taskId 任务ID
     * @return true 更新成功；false 状态已经不是PENDING，任务被外部变更过
     */
    private boolean markRunning(Long taskId) {
        return taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, taskId)
                .eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                .set(TaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                .set(TaskEntity::getStartTime, LocalDateTime.now())) > 0;
    }

    /**
     * 标记任务为已完成COMPLETED，写入结果摘要、结束时间
     * @param task 任务实体
     * @param summary Agent执行结果摘要文本
     */
    private void complete(TaskEntity task, String summary) {
        taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .eq(TaskEntity::getStatus, TaskStatus.RUNNING.getCode())
                .set(TaskEntity::getStatus, TaskStatus.COMPLETED.getCode())
                .set(TaskEntity::getResultSummary, summary)
                .set(TaskEntity::getEndTime, LocalDateTime.now()));
    }

    /**
     * 任务执行失败处理：控制重试逻辑、状态流转、审计日志、MQ应答
     * <p>
     * 1.任务已经被用户手动终止TERMINATED，直接ack；
     * 2.未达到最大重试次数：重试计数+1，重置状态PENDING，重新发布MQ消息；
     * 3.达到最大重试次数：置为FAILED，记录错误信息与结束时间，reject丢弃消息。
     * </p>
     * @param taskId 任务ID
     * @param ex 执行抛出的异常
     * @param channel MQ通道
     * @param tag deliveryTag
     * @throws IOException MQ IO异常
     */
    private void handleFailure(Long taskId, Exception ex, Channel channel, long tag) throws IOException {
        TaskEntity task = taskService.require(taskId);
        // 任务已经被外部手动终止，不再重试，直接确认消息
        if (TaskStatus.fromCode(task.getStatus()) == TaskStatus.TERMINATED) {
            channel.basicAck(tag, false);
            return;
        }
        int retries = task.getRetryCount() == null ? 0 : task.getRetryCount();

        // 判断是否还允许重试
        if (retries < TaskConstant.MAX_TASK_RETRY_COUNT) {
            // 重置任务状态为待执行，重试计数自增，记录错误信息
            taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                    .eq(TaskEntity::getId, taskId)
                    .set(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                    .set(TaskEntity::getRetryCount, retries + TaskConstant.RETRY_COUNT_INCREMENT)
                    .set(TaskEntity::getErrorMessage, safeMessage(ex)));
            try {
                // 重新投递任务消息，ack当前旧消息
                messagePublisher.publish(taskId);
                channel.basicAck(tag, false);
                // 审计日志
                auditLogService.recordAgent(task.getSpaceId(), task.getId(), task.getAgentId(),
                        AuditAction.TASK_RETRY, AuditTargetType.TASK, task.getId(), safeMessage(ex));
                return;
            } catch (RuntimeException publishError) {
                // 重发消息本身失败，降级进入最终失败逻辑
                ex = publishError;
            }
        }

        // 超过最大重试次数，标记任务为永久失败
        taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, taskId)
                .set(TaskEntity::getStatus, TaskStatus.FAILED.getCode())
                .set(TaskEntity::getErrorMessage, safeMessage(ex))
                .set(TaskEntity::getEndTime, LocalDateTime.now()));
        auditLogService.recordAgent(task.getSpaceId(), task.getId(), task.getAgentId(),
                AuditAction.TASK_FAILED, AuditTargetType.TASK, task.getId(), safeMessage(ex));
        // 拒绝消息，不再重回队列
        channel.basicReject(tag, false);
    }

    /**
     * 获取安全截断后的异常信息，防止超长错误文本入库
     * @param ex 原始异常
     * @return 截断后错误字符串；message为null返回异常类名
     */
    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), TaskConstant.MAX_ERROR_MESSAGE_LENGTH));
    }

    /**
     * Feign远程调用返回结果统一包装，非成功抛出业务异常
     * @param result feign调用返回Result
     * @return result.data()
     * @param <T> data类型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}
