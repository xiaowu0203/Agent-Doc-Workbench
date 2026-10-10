package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineExecutionFactVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import lombok.extern.slf4j.Slf4j;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/** 独立于 Task 终态和账本；取消请求不等于运行终止。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OnlineExecutionObservationService {
    private static final List<String> TERMINAL = List.of("COMPLETED", "FAILED", "CANCELED", "TIMED_OUT");
    private final AgentExecutionMapper executions;
    private final AgentExecutionQueryService query;
    private final OnlineEvaluationFeign evaluation;
    private final OnlineDocumentFeign document;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public List<OnlineExecutionFactVO> facts(String experimentId, String token, List<String> ids) {
        var bindings = authorize(experimentId, token, OnlineCapabilityPurpose.ONLINE_OBSERVE, ids);
        var rows = rows(bindings);
        return bindings.stream().map(binding -> {
            var row = rows.get(OnlineProtocolUtils.id(binding.taskId()));
            String hash = OnlineProtocolUtils.hash("online.binding", binding);
            if (row == null) { return new OnlineExecutionFactVO(new OnlineTaskFactVO(binding.taskId(), hash, null,
                    null, "ABSENT", null, false, false, null), null, null, null); }
            requireIdentity(row, binding, hash);
            var origin = row.getStartedAt() == null ? row.getCreatedAt() : row.getStartedAt();
            String status = !TERMINAL.contains(row.getStatus()) && origin != null
                    && !LocalDateTime.now().isBefore(origin.plusSeconds(binding.executionTimeoutSeconds())) ? "UNKNOWN" : row.getStatus();
            return new OnlineExecutionFactVO(new OnlineTaskFactVO(binding.taskId(), hash, null, row.getId().toString(),
                    status, null, false, Boolean.TRUE.equals(row.getCancelRequested()),
                    row.getFinishedAt() == null ? null : row.getFinishedAt().toString()), row.getA2aTaskId(), row.getA2aContextId(), tokenUsage(row));
        }).toList();
    }

    private AgentExecutionTokenUsageVO tokenUsage(AgentExecutionEntity row) {
        try { return query.tokenUsageOf(row); }
        catch (RuntimeException unavailable) {
            log.warn("线上冻结费用投影不可用 executionId={} failureType={}", row.getId(), unavailable.getClass().getSimpleName());
            return null;
        }
    }

    public void cancel(String experimentId, String token, List<String> ids) {
        var bindings = authorize(experimentId, token, OnlineCapabilityPurpose.ONLINE_CANCEL, ids);
        var rows = rows(bindings);
        for (var binding : bindings) {
            var row = rows.get(OnlineProtocolUtils.id(binding.taskId()));
            if (row != null) { requireIdentity(row, binding, OnlineProtocolUtils.hash("online.binding", binding)); }
        }
        executions.update(null, new LambdaUpdateWrapper<AgentExecutionEntity>().in(AgentExecutionEntity::getWorkbenchTaskId,
                bindings.stream().map(binding -> OnlineProtocolUtils.id(binding.taskId())).toList())
                .notIn(AgentExecutionEntity::getStatus, TERMINAL).set(AgentExecutionEntity::getCancelRequested, true));
    }
    private List<OnlineTaskBindingDTO> authorize(String experimentId, String token, OnlineCapabilityPurpose purpose, List<String> rawIds) {
        var ids = OnlineCapabilityUtils.taskIds(rawIds);
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        verifier.verify(token, purpose, experimentId, ids);
        var result = evaluation.scopedBindings(experimentId, purpose, token, ids);
        var permitted = document.scopedProtectionPermission(experimentId, purpose, token, ids);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null || result.data().size() != ids.size()
                || !ids.equals(OnlineCapabilityUtils.taskIds(result.data().stream().map(OnlineTaskBindingDTO::taskId).toList()))
                || permitted == null || permitted.code() != ErrorCode.SUCCESS.getCode()) { throw denied(); }
        return result.data();
    }
    private Map<Long, AgentExecutionEntity> rows(List<OnlineTaskBindingDTO> bindings) {
        return executions.selectList(new LambdaQueryWrapper<AgentExecutionEntity>().in(AgentExecutionEntity::getWorkbenchTaskId,
                bindings.stream().map(value -> OnlineProtocolUtils.id(value.taskId())).toList())).stream()
                .collect(Collectors.toMap(AgentExecutionEntity::getWorkbenchTaskId, value -> value));
    }
    private static void requireIdentity(AgentExecutionEntity row, OnlineTaskBindingDTO binding, String hash) {
        if (!binding.experimentId().equals(String.valueOf(row.getOnlineExperimentId())) || !binding.assignmentId().equals(String.valueOf(row.getOnlineAssignmentId()))
                || !binding.spaceId().equals(String.valueOf(row.getSpaceId())) || !binding.agentId().equals(String.valueOf(row.getAgentId()))
                || !Integer.valueOf(binding.schemaVersion()).equals(row.getOnlineBindingSchemaVersion()) || !hash.equals(row.getOnlineBindingHash())) { throw denied(); }
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
