package com.agentdoc.task.a2a;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.task.convertor.A2aTaskConvertor;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.spec.Task;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A2A任务状态同步服务
 * <p>
 * 负责将远端Agent‑Server返回的任务状态数据同步更新到本地数据库。
 * 使用条件更新，仅当本地任务仍处于远端活跃状态时才允许更新，防止终态被覆盖。
 * 远端查询/草稿收尾在本地事务外执行，终态与权威账本/隔离产物在同一短事务内提交。
 * 回调链路、定时对账任务均会调用该服务完成状态落地。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class A2aTaskSynchronizationService {

    private final AgentFeign agentFeign;
    private final DocumentFeign documentFeign;
    private final TaskCapabilityCryptoService cryptoService;
    private final TaskTerminalPersistenceService persistenceService;

    /**
     * 执行远端任务 → 本地任务实体状态同步并落库
     * <p>
     * 流程：校验远端身份 → 获取终态账本投影 → 必要的草稿收尾 → 短事务条件写回；
     * 更新条件：主键匹配 且 当前本地任务处于远端活跃状态，避免已完结任务被回调/对账覆盖。
     * 更新行数为0代表条件不满足，直接返回false。
     * 任务为完成/失败/取消终态时，同事务调用Token用量服务记录消耗账单。
     * </p>
     *
     * @param task        本地任务实体，会被{@link A2aTaskConvertor#apply(TaskEntity, Task)}回填远端数据
     * @param remoteTask  远端A2A协议任务对象
     * @return true：数据库更新成功；false：条件不满足未执行更新
     * @throws BusinessException 获取Agent执行档案接口调用异常时抛出
     */
    public boolean synchronize(TaskEntity task, Task remoteTask) {
        return synchronize(task, remoteTask, () -> true);
    }

    /** 普通对账使用相同短事务，并在远程收尾前后验证锁所有权。 */
    public boolean synchronize(TaskEntity task, Task remoteTask, BooleanSupplier ownsLock) {
        if (!TaskStatus.remoteActiveCodes().contains(task.getStatus())) { return false; }
        Long boundExecution = task.getAgentExecutionId();
        requireRemoteIdentity(task, remoteTask);
        // 将远端A2A任务数据转换、回填到本地task对象
        applyRemote(task, remoteTask);
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        A2aTokenUsage usage = isTerminal(status) ? resolveTokenUsage(task) : null;
        requireExecutionBinding(task, boundExecution, usage);
        requireLock(ownsLock);
        // 执行模式为实时LIVE、文档类型是草稿，且任务终态（完成/终止/失败）时，执行草稿文档收尾处理
        if (TaskExecutionMode.LIVE.name().equals(task.getExecutionMode())
                && DocType.fromCode(task.getDocumentType()) == DocType.DRAFT
                && (status == TaskStatus.COMPLETED || status == TaskStatus.TERMINATED
                || status == TaskStatus.FAILED)) {
            finalizeDraft(task, status);
        }
        return persistenceService.persist(task, usage, ownsLock);
    }

    /** 恢复路径已完成独立草稿收尾；直接消费受控查询附带的账本，不再用原凭证做 RPC。 */
    public boolean synchronizeRecovered(TaskEntity task, Task remoteTask, AgentExecutionTokenUsageVO projection,
                                         BooleanSupplier ownsLock) {
        if (!TaskStatus.remoteActiveCodes().contains(task.getStatus())) { return false; }
        Long boundExecution = task.getAgentExecutionId();
        requireRemoteIdentity(task, remoteTask);
        applyRemote(task, remoteTask);
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        A2aTokenUsage usage = isTerminal(status) ? tokenUsage(requireTokenUsage(Result.ok(projection))) : null;
        requireExecutionBinding(task, boundExecution, usage);
        return persistenceService.persist(task, usage, ownsLock);
    }

    private void requireRemoteIdentity(TaskEntity task, Task remote) {
        if (remote == null || remote.status() == null || remote.status().state() == null
                || (task.getA2aTaskId() != null && !task.getA2aTaskId().equals(remote.id()))
                || (task.getA2aContextId() != null && !task.getA2aContextId().equals(remote.contextId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "RECOVERY_IDENTITY_MISMATCH");
        }
    }

    private void applyRemote(TaskEntity task, Task remote) {
        boolean cancelRequested = TaskStatus.CANCELING.getCodeEquals(task.getStatus());
        A2aTaskConvertor.apply(task, remote);
        // 活跃远端状态不能抹掉已落库的取消意图；否则下一轮无法申请窄取消动作。
        if (cancelRequested && !remote.status().state().isFinal()) { task.setStatus(TaskStatus.CANCELING.getCode()); }
    }

    private void requireExecutionBinding(TaskEntity task, Long bound, A2aTokenUsage usage) {
        Long executionId = usage == null ? task.getAgentExecutionId() : usage.executionId();
        if ((bound != null && !Objects.equals(bound, executionId))
                || (usage != null && task.getAgentExecutionId() != null
                && !Objects.equals(task.getAgentExecutionId(), executionId))) {
            throw new BusinessException(ErrorCode.CONFLICT, "RECOVERY_IDENTITY_MISMATCH");
        }
        task.setAgentExecutionId(executionId);
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.COMPLETED || status == TaskStatus.TERMINATED || status == TaskStatus.FAILED;
    }

    private void requireLock(BooleanSupplier ownsLock) {
        if (!ownsLock.getAsBoolean()) { throw new BusinessException(ErrorCode.CONFLICT, "RECOVERY_CAPACITY_EXCEEDED"); }
    }

    /**
     * 完成Agent草稿处理
     * 根据任务最终状态，执行【提交草稿】或【丢弃草稿】操作
     * 仅当存在文档ID与能力令牌时才执行，否则直接返回
     * @param task 当前任务实体
     * @param status 任务最终状态：COMPLETED提交草稿，其他状态丢弃草稿
     */
    private void finalizeDraft(TaskEntity task, TaskStatus status) {
        // 缺少文档ID或能力令牌，不执行草稿处理
        if (task.getDocumentId() == null || task.getCapabilityToken() == null) {
            return;
        }
        // 解密获取访问文档的能力令牌
        String capability = cryptoService.decrypt(task.getCapabilityToken());
        if (status == TaskStatus.COMPLETED) {
            // 任务完成：提交Agent生成的草稿变更到文档
            Result<?> result = documentFeign.finalizeDraftAgentChanges(task.getDocumentId(), capability);
            requireSuccess(result, "提交草稿暂存失败");
        } else {
            // 任务异常/取消：丢弃Agent草稿变更
            Result<?> result = documentFeign.discardDraftAgentChanges(task.getDocumentId(), capability);
            requireSuccess(result, "丢弃草稿暂存失败");
        }
    }

    /**
     * 解析任务Token用量信息
     * AgentExecution 是 Token 和价格快照的权威来源，终态统一读取内部投影。
     * @param task 本地任务实体
     * @return A2A标准Token用量对象
     */
    private A2aTokenUsage resolveTokenUsage(TaskEntity task) {
        AgentExecutionTokenUsageVO usageProjection = requireTokenUsage(agentFeign.getExecutionTokenUsage(task.getId()));
        return tokenUsage(usageProjection);
    }

    private A2aTokenUsage tokenUsage(AgentExecutionTokenUsageVO usageProjection) {
        return new A2aTokenUsage(usageProjection.inputTokens(), usageProjection.cachedInputTokens(),
                usageProjection.outputTokens(), Boolean.TRUE.equals(usageProjection.inputTokensEstimated()),
                Boolean.TRUE.equals(usageProjection.cachedInputTokensEstimated()),
                Boolean.TRUE.equals(usageProjection.outputTokensEstimated()), usageProjection.executionId(),
                usageProjection.modelId(), usageProjection.modelConfigVersion(),
                usageProjection.inputPricePerMillion(), usageProjection.outputPricePerMillion(),
                usageProjection.currency(), usageProjection.pricingSchemaVersion(),
                usageProjection.pricingCapturedAt());
    }

    private AgentExecutionTokenUsageVO requireTokenUsage(Result<AgentExecutionTokenUsageVO> result) {
        if (result != null && result.code() == ErrorCode.SUCCESS.getCode() && result.data() != null) {
            return result.data();
        }
        throw new BusinessException(ErrorCode.CONFLICT, "无法读取 AgentExecution Token 权威账本投影");
    }

    /**
     * Feign调用结果校验工具方法
     * 判断远程调用Result是否成功，失败则抛出业务异常
     * @param result feign返回结果对象
     * @param message 自定义错误提示信息（当result返回null时使用）
     */
    private void requireSuccess(Result<?> result, String message) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null || result.message() == null ? message : result.message());
        }
    }

}
