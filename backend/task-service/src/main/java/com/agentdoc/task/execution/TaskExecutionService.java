package com.agentdoc.task.execution;

import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.config.RabbitTaskConfiguration;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.convertor.A2aTaskConvertor;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.agentdoc.task.service.AuditLogService;
import com.agentdoc.task.service.TaskMessagePublisher;
import com.agentdoc.task.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.a2aproject.sdk.spec.Task;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * RabbitMQ 任务消费者和 A2A 任务分发器。
 * <p>
 * 监听 RabbitMQ 任务队列，将 Workbench Task 幂等分发给远程 Agent Server。
 * 核心职责：消息消费防重复、分布式锁、任务状态流转、A2A 调用、异常重试和审计日志。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final A2aTaskClient a2aTaskClient;
    private final TaskMessagePublisher messagePublisher;
    private final RedisUtils redisUtils;
    private final TaskCapabilityCryptoService cryptoService;
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
            // 数据库乐观锁：将任务状态由 PENDING 更新为 DISPATCHED，并记录开始时间
            if (!markDispatched(taskId)) {
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
     * 将任务提交到 Agent Server，并记录远端 A2A Task 标识和初始状态。
     * <p>
     * Agent 后续通过 Task Capability 调用 Workbench MCP Server，执行状态和结果通过 A2A 回调同步。
     * </p>
     * @param task 待执行任务实体
     */
    private void execute(TaskEntity task) {
        // 生成 A2A Task Capability，并加密存储到数据库
        String capability = cryptoService.decrypt(task.getCapabilityToken());
        // 调用 A2A Client，将任务提交到 Agent Server
        Task remoteTask = a2aTaskClient.send(task, capability);
        if (remoteTask == null) {
            throw new IllegalStateException("Agent Server 未返回 A2A Task");
        }
        // 将remoteTask信息回填到task中
        A2aTaskConvertor.apply(task, remoteTask);
        // 更新任务
        taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .isNull(TaskEntity::getA2aTaskId)
                .set(TaskEntity::getA2aTaskId, task.getA2aTaskId())
                .set(TaskEntity::getA2aContextId, task.getA2aContextId())
                .set(TaskEntity::getStatus, task.getStatus())
                .set(TaskEntity::getDispatchedAt, LocalDateTime.now())
                .set(TaskEntity::getLastHeartbeatAt, task.getLastHeartbeatAt()));
    }

    /**
     * 乐观锁将任务状态由 PENDING 更新为 DISPATCHED，并记录开始时间。
     * @param taskId 任务ID
     * @return true 更新成功；false 状态已经不是PENDING，任务被外部变更过
     */
    private boolean markDispatched(Long taskId) {
        return taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, taskId)
                .eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                .set(TaskEntity::getStatus, TaskStatus.DISPATCHED.getCode())
                .set(TaskEntity::getStartTime, LocalDateTime.now())) > 0;
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

}
