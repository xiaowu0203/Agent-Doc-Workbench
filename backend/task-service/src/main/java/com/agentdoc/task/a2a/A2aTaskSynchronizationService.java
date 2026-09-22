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
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.agentdoc.task.service.TokenUsageService;
import com.agentdoc.task.service.ExecutionArtifactService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.spec.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A2A任务状态同步服务
 * <p>
 * 负责将远端Agent‑Server返回的任务状态数据同步更新到本地数据库。
 * 使用条件更新，仅当本地任务仍处于远端活跃状态时才允许更新，防止终态被覆盖。
 * 更新成功后，如果任务已完成，记录Token消耗用量；内部调用Agent服务Feign接口获取执行配置档案。
 * 回调链路、定时对账任务均会调用该服务完成状态落地。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class A2aTaskSynchronizationService {

    private final TaskMapper taskMapper;
    private final AgentFeign agentFeign;
    private final DocumentFeign documentFeign;
    private final TokenUsageService tokenUsageService;
    private final TaskCapabilityCryptoService cryptoService;
    private final ExecutionArtifactService executionArtifactService;

    /**
     * 执行远端任务 → 本地任务实体状态同步并落库
     * <p>
     * 流程：转换器把远端数据写入task实体 → 条件式数据库更新；
     * 更新条件：主键匹配 且 当前本地任务处于远端活跃状态，避免已完结任务被回调/对账覆盖。
     * 更新行数为0代表条件不满足，直接返回false。
     * 任务状态为完成时，调用Token用量服务记录消耗账单。
     * </p>
     *
     * @param task        本地任务实体，会被{@link A2aTaskConvertor#apply(TaskEntity, Task)}回填远端数据
     * @param remoteTask  远端A2A协议任务对象
     * @return true：数据库更新成功；false：条件不满足未执行更新
     * @throws BusinessException 获取Agent执行档案接口调用异常时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean synchronize(TaskEntity task, Task remoteTask) {
        // 将远端A2A任务数据转换、回填到本地task对象
        A2aTaskConvertor.apply(task, remoteTask);
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        // 任务完成，且执行模式为隔离模式，记录结果摘要
        if (status == TaskStatus.COMPLETED
                && TaskExecutionMode.ISOLATED.name().equals(task.getExecutionMode())) {
            if (task.getAgentExecutionId() == null) {
                AgentExecutionTokenUsageVO usage = requireTokenUsage(agentFeign.getExecutionTokenUsage(task.getId()));
                task.setAgentExecutionId(usage.executionId());
            }
            executionArtifactService.appendResultSummary(task);
        }
        // 条件更新：仅本地任务还处于远端活跃状态，才允许覆盖状态，保护已终态数据
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .in(TaskEntity::getStatus, TaskStatus.remoteActiveCodes())
                .set(TaskEntity::getA2aTaskId, task.getA2aTaskId())
                .set(TaskEntity::getA2aContextId, task.getA2aContextId())
                .set(TaskEntity::getStatus, task.getStatus())
                .set(TaskEntity::getLastHeartbeatAt, task.getLastHeartbeatAt())
                .set(TaskEntity::getEndTime, task.getEndTime())
                .set(TaskEntity::getResultSummary, task.getResultSummary())
                .set(TaskEntity::getErrorMessage, task.getErrorMessage())
                .set(TaskEntity::getTokensUsed, task.getTokensUsed())
                .set(TaskEntity::getTokensEstimated, task.getTokensEstimated())
                .set(TaskEntity::getAgentExecutionId, task.getAgentExecutionId())
                .set(TaskEntity::getPromptHash, task.getPromptHash()));
        // 更新行数为0：说明任务已经不是远端活跃状态，放弃同步
        if (updated == 0) {
            return false;
        }
        // 执行模式为实时LIVE、文档类型是草稿，且任务终态（完成/终止/失败）时，执行草稿文档收尾处理
        if (TaskExecutionMode.LIVE.name().equals(task.getExecutionMode())
                && DocType.fromCode(task.getDocumentType()) == DocType.DRAFT
                && (status == TaskStatus.COMPLETED || status == TaskStatus.TERMINATED
                || status == TaskStatus.FAILED)) {
            finalizeDraft(task, status);
        }
        if (status == TaskStatus.COMPLETED || status == TaskStatus.TERMINATED || status == TaskStatus.FAILED) {
            tokenUsageService.recordRemote(task, resolveTokenUsage(task));
        }
        return true;
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
