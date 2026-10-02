package com.agentdoc.task.a2a;

import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.service.TaskRecoveryService;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.a2aproject.sdk.spec.Task;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * A2A任务状态对账服务
 * <p>
 * 定时补偿任务，用于处理回调丢失、网络异常导致本地与远端Agent‑Server状态不一致问题。
 * 扫描远端活跃、心跳超时的本地任务，通过主动调用A2A查询接口拉取远端最新状态，执行状态同步。
 * 使用Redis分布式锁避免多实例并发重复对账同一个任务。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2aTaskReconciliationService {

    private final TaskMapper taskMapper;
    private final A2aTaskClient a2aTaskClient;
    private final A2aTaskSynchronizationService synchronizationService;
    private final TaskCapabilityCryptoService cryptoService;
    private final RedisUtils redisUtils;
    private final A2aProperties a2aProperties;
    private final TaskRecoveryService recoveryService;
    /** 按主键循环扫描，失败任务不能永久占据首批并饿死后续任务。 */
    private long scanAfter;

    /**
     * A2A过期任务定时对账入口
     * <p>调度间隔由配置 agent‑doc.a2a.reconcile‑delay‑ms 控制，fixedDelay 模式：上一轮执行完成后再等待指定间隔执行下一轮。
     * 查询条件：任务为远端活跃状态、存在A2A远端任务ID、心跳为空或者心跳早于过期时间点；按主键循环扫描避免失败任务饿死后续任务。</p>
     */
    @Scheduled(fixedDelayString = "${agent-doc.a2a.reconcile-delay-ms}")
    public void reconcileStaleTasks() {
        // 计算心跳截止时间：当前时间往前回溯 staleSeconds
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(a2aProperties.getReconcileStaleSeconds());
        Page<TaskEntity> page = scan(cutoff);
        if (page.getRecords().isEmpty() && scanAfter != 0) {
            scanAfter = 0;
            page = scan(cutoff);
        }
        if (page.getTotal() > a2aProperties.getReconcileBatchSize() && !page.getRecords().isEmpty()) {
            recoveryService.reportCapacity(page.getRecords().getFirst());
        }
        for (TaskEntity task : page.getRecords()) {
            scanAfter = task.getId();
            reconcile(task);
        }
    }

    private Page<TaskEntity> scan(LocalDateTime cutoff) {
        return taskMapper.selectPage(new Page<>(1, a2aProperties.getReconcileBatchSize()), new LambdaQueryWrapper<TaskEntity>()
                        // 只筛选远端仍处于活跃中的任务状态
                        .in(TaskEntity::getStatus, TaskStatus.remoteActiveCodes())
                        // 必须存在远端A2A任务ID，才可以去远端查询
                        .isNotNull(TaskEntity::getA2aTaskId)
                        // 心跳为null 或者 心跳时间早于阈值，判定为过期待对账
                        .and(wrapper -> wrapper.isNull(TaskEntity::getLastHeartbeatAt)
                                .or().le(TaskEntity::getLastHeartbeatAt, cutoff))
                        .gt(scanAfter != 0, TaskEntity::getId, scanAfter)
                        .orderByAsc(TaskEntity::getId));
    }

    /**
     * 对单个A2A任务执行状态对账补偿
     * <p>获取Redis分布式锁防止多实例并发处理同一任务；解密任务能力令牌；调用远端查询接口；同步远端状态至本地。
     * 单个任务异常不打断批次；恢复失败通过结构化去重告警呈现，finally 按 owner 比对释放锁。</p>
     *
     * @param candidate 扫描获得的候选任务；获得锁后重新读取
     */
    void reconcile(TaskEntity candidate) {
        // 构建任务对账分布式锁key
        String lockKey = RedisKeyConstants.TASK_A2A_RECONCILE_LOCK_PREFIX + candidate.getId();
        String owner = UUID.randomUUID().toString();
        long started = System.nanoTime();
        BooleanSupplier ownsLock = () -> owner.equals(redisUtils.get(lockKey))
                && System.nanoTime() - started < Duration.ofSeconds(TaskRecoveryConstant.PROCESS_BUDGET_SECONDS).toNanos();
        // 获取锁失败，说明其它实例正在处理，直接跳过
        if (!redisUtils.setIfAbsent(lockKey, owner,
                Duration.ofSeconds(TaskConstant.A2A_RECONCILE_LOCK_SECONDS))) {
            return;
        }
        try {
            TaskEntity task = taskMapper.selectById(candidate.getId());
            if (task == null || !TaskStatus.remoteActiveCodes().contains(task.getStatus()) || task.getA2aTaskId() == null) { return; }
            boolean requiresRecovery;
            try { requiresRecovery = recoveryService.hasExpiredProof(task); }
            catch (RuntimeException exception) { requiresRecovery = true; }
            if (requiresRecovery) {
                recoveryService.recoverLocked(task, ownsLock, null);
                return;
            }
            // 解密存储的加密能力令牌
            String capability = cryptoService.decrypt(task.getCapabilityToken());
            // HTTP调用Agent‑Server拉取远端最新任务信息
            Task remoteTask = a2aTaskClient.getForReconciliation(task.getA2aTaskId(), capability);
            if (remoteTask != null) {
                // token设置到上下文中
                AuthorizationContext.set("Bearer " + capability);
                TaskCapabilityContext.set(capability);
                // HTTP调用Agent‑Server拉取远端最新任务信息
                synchronizationService.synchronize(task, remoteTask, ownsLock);
            }
        } catch (RuntimeException exception) {
            // 过期证明的告警由恢复流程去重；普通异常不输出凭证/HTTP 请求或响应。
            log.debug("A2A 任务状态对账未完成，taskId={}，errorType={}", candidate.getId(), exception.getClass().getSimpleName());
        } finally {
            // 清理上下文token
            AuthorizationContext.clear();
            TaskCapabilityContext.clear();
            // 无论成功失败，释放分布式锁
            redisUtils.deleteIfValueMatches(lockKey, owner);
        }
    }
}
