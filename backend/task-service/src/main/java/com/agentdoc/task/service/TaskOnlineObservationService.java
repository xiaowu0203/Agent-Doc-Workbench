package com.agentdoc.task.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Task 与 Token 权威账本的批量投影；永不读取当前价格补历史账本。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineObservationService {
    private final TaskMapper tasks;
    private final TokenUsageDetailMapper ledger;
    private final OnlineEvaluationFeign evaluation;
    private final OnlineDocumentFeign document;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public List<OnlineTaskFactVO> facts(String experimentId, String token, List<String> rawIds) {
        var bindings = authorize(experimentId, token, OnlineCapabilityPurpose.ONLINE_OBSERVE, rawIds);
        var rows = rows(bindings);
        var accounts = ledger.selectList(new LambdaQueryWrapper<TokenUsageDetailEntity>().in(TokenUsageDetailEntity::getTaskId,
                bindings.stream().map(value -> OnlineProtocolUtils.id(value.taskId())).toList())).stream()
                .collect(Collectors.toMap(TokenUsageDetailEntity::getTaskId, value -> value));
        return bindings.stream().map(binding -> {
            var row = rows.get(OnlineProtocolUtils.id(binding.taskId())); String hash = OnlineProtocolUtils.hash("online.binding", binding);
            if (row == null) { return new OnlineTaskFactVO(binding.taskId(), hash, "ABSENT", null, null, null, false, false, null); }
            requireIdentity(row, binding, hash);
            var account = accounts.get(row.getId()); String consumed = null;
            if (account != null) {
                if (!Objects.equals(account.getExecutionId(), row.getAgentExecutionId()) || !Objects.equals(account.getAgentId(), row.getAgentId())
                        || !Objects.equals(account.getSpaceId(), row.getSpaceId())) { throw denied(); }
                if (account.getInputTokens() != null && account.getOutputTokens() != null) {
                    if (account.getInputTokens() < 0 || account.getOutputTokens() < 0) { throw denied(); }
                    consumed = BigInteger.valueOf(account.getInputTokens()).add(BigInteger.valueOf(account.getOutputTokens())).toString();
                }
            }
            boolean never = row.getA2aTaskId() == null && row.getAgentExecutionId() == null && row.getStartTime() == null && row.getDispatchedAt() == null;
            return new OnlineTaskFactVO(binding.taskId(), hash, TaskStatus.fromCode(row.getStatus()).name(),
                    row.getAgentExecutionId() == null ? null : row.getAgentExecutionId().toString(), null, consumed, never,
                    TaskStatus.CANCELING.getCodeEquals(row.getStatus()) || TaskStatus.TERMINATED.getCodeEquals(row.getStatus()), null);
        }).toList();
    }

    public void cancel(String experimentId, String token, List<String> rawIds) {
        var bindings = authorize(experimentId, token, OnlineCapabilityPurpose.ONLINE_CANCEL, rawIds);
        var rows = rows(bindings);
        for (var binding : bindings) {
            var row = rows.get(OnlineProtocolUtils.id(binding.taskId()));
            if (row != null) { requireIdentity(row, binding, OnlineProtocolUtils.hash("online.binding", binding)); }
        }
        var ids = bindings.stream().map(value -> OnlineProtocolUtils.id(value.taskId())).toList();
        // Agent 新执行准入复查 Task 行；只有从未派发的 PENDING 才能在本地确证终止。
        tasks.update(null, new LambdaUpdateWrapper<TaskEntity>().in(TaskEntity::getId, ids).eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                .isNull(TaskEntity::getStartTime).isNull(TaskEntity::getA2aTaskId).isNull(TaskEntity::getAgentExecutionId)
                .set(TaskEntity::getStatus, TaskStatus.TERMINATED.getCode()).set(TaskEntity::getEndTime, LocalDateTime.now()));
        tasks.update(null, new LambdaUpdateWrapper<TaskEntity>().in(TaskEntity::getId, ids).in(TaskEntity::getStatus, TaskStatus.remoteActiveCodes())
                .set(TaskEntity::getStatus, TaskStatus.CANCELING.getCode()));
    }

    private List<OnlineTaskBindingDTO> authorize(String experimentId, String token, OnlineCapabilityPurpose purpose, List<String> rawIds) {
        var ids = OnlineCapabilityUtils.taskIds(rawIds); var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        verifier.verify(token, purpose, experimentId, ids);
        var result = evaluation.scopedBindings(experimentId, purpose, token, ids);
        var permitted = document.scopedProtectionPermission(experimentId, purpose, token, ids);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null || result.data().size() != ids.size()
                || !ids.equals(OnlineCapabilityUtils.taskIds(result.data().stream().map(OnlineTaskBindingDTO::taskId).toList()))
                || permitted == null || permitted.code() != ErrorCode.SUCCESS.getCode()) { throw denied(); }
        return result.data();
    }
    private Map<Long, TaskEntity> rows(List<OnlineTaskBindingDTO> bindings) {
        return tasks.selectList(new LambdaQueryWrapper<TaskEntity>().in(TaskEntity::getId,
                bindings.stream().map(value -> OnlineProtocolUtils.id(value.taskId())).toList())).stream()
                .collect(Collectors.toMap(TaskEntity::getId, value -> value));
    }
    public static void requireIdentity(TaskEntity row, OnlineTaskBindingDTO binding, String hash) {
        if (!binding.experimentId().equals(String.valueOf(row.getOnlineExperimentId())) || !binding.assignmentId().equals(String.valueOf(row.getOnlineAssignmentId()))
                || !binding.spaceId().equals(String.valueOf(row.getSpaceId())) || !binding.agentId().equals(String.valueOf(row.getAgentId()))
                || !binding.documentId().equals(String.valueOf(row.getDocumentId())) || !binding.actorId().equals(String.valueOf(row.getCreatedBy()))
                || !Integer.valueOf(binding.schemaVersion()).equals(row.getOnlineBindingSchemaVersion()) || !hash.equals(row.getOnlineBindingHash())
                || !binding.inputHash().equals(row.getInputSnapshotHash()) || !binding.documentVersion().equals(String.valueOf(row.getDocumentVersionSnapshot()))
                || !binding.documentContentHash().equals(row.getDocumentContentSha256()) || !binding.tokenBudget().equals(String.valueOf(row.getTokenBudget()))
                || !binding.lineageType().equals(row.getLineageType()) || !binding.executionMode().equals(row.getExecutionMode())) { throw denied(); }
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
