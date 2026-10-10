package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAuthFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.feign.vo.OnlineExecutionFactVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.config.OnlineReconciliationProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import static com.agentdoc.evaluation.constant.OnlineExperimentConstant.UNRESOLVED_SECONDS;

/** 有界批次扫描，RPC 不持数据库锁；各来源异常独立留 UNKNOWN，不补零或强制释放。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineReconciliationService {
    private final OnlineReconciliationProperties properties;
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final OnlineExecutionAuthority authority;
    private final OnlineControlAccessService control;
    private final WorkerCapabilityCryptoService crypto;
    private final OnlineAuthFeign auth;
    private final OnlineAgentFeign agent;
    private final OnlineTaskFeign task;
    private final OnlineDocumentFeign document;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;
    private final Map<Long, Long> cursors = new ConcurrentHashMap<>();
    private final Map<Long, Long> alerts = new ConcurrentHashMap<>();
    private long experimentCursor;

    @Scheduled(fixedDelayString = "${agent-doc.online.reconcile-delay-ms:30000}")
    public void scan() {
        if (!properties.isEnabled()) { return; }
        var batch = experiments.selectList(new LambdaQueryWrapper<OnlineExperimentEntity>().in(OnlineExperimentEntity::getStatus, List.of("ACTIVE", "PAUSED", "STOPPING"))
                .gt(OnlineExperimentEntity::getId, experimentCursor).orderByAsc(OnlineExperimentEntity::getId).last("LIMIT " + OnlineCapabilityConstant.MAX_TASK_COUNT));
        if (batch.isEmpty()) { experimentCursor = 0; return; }
        for (var experiment : batch) { reconcile(experiment.getId()); }
        experimentCursor = batch.getLast().getId();
    }

    public void reconcile(Long id) {
        var experiment = experiments.selectById(id); if (experiment == null || "STOPPED".equals(experiment.getStatus())) { cursors.remove(id); alerts.remove(id); return; }
        try {
            if ("ACTIVE".equals(experiment.getStatus()) && experiment.getAssignmentDeadline() != null
                    && !LocalDateTime.now().isBefore(experiment.getAssignmentDeadline())) {
                authority.closeGate(id, experiment.getStateVersion(), null, false, OnlineReasonCode.ONLINE_ASSIGNMENT_DEADLINE.name());
                experiment = experiments.selectById(id);
            }
            // 超龄未知保护独立于游标和单批容量，不等旧分配重新扫描到才关门。
            if (assignments.selectCount(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getExperimentId, id)
                    .eq(OnlineAssignmentEntity::getSettlementStatus, "UNKNOWN").le(OnlineAssignmentEntity::getUnresolvedSince, LocalDateTime.now().minusSeconds(UNRESOLVED_SECONDS))) > 0) {
                authority.safetyPause(id, OnlineReasonCode.ONLINE_FACT_UNKNOWN.name());
            }
            String token = renew(experiment, control.require(id));
            try {
                if (!authority.manifest(experiment).path("dependencyHash").asText().equals(data(agent.dependency(id.toString(), token)))) {
                    authority.safetyPause(id, OnlineReasonCode.DEPENDENCY_DRIFT.name());
                }
                requireSuccess(document.requireResources(id.toString(), token));
            } catch (RuntimeException drift) { authority.safetyPause(id, OnlineReasonCode.DEPENDENCY_DRIFT.name()); alert(id, "DEPENDENCY_OR_RESOURCE_UNAVAILABLE"); }
            authority.runtimeSrm(id);
            var rows = page(id, cursors.getOrDefault(id, 0L));
            if (rows.isEmpty()) { cursors.put(id, 0L); rows = page(id, 0L); }
            if (rows.isEmpty()) { return; }
            var ids = OnlineCapabilityUtils.taskIds(rows.stream().map(row -> row.getTaskId().toString()).toList());
            try { requireSuccess(task.repair(id.toString(), token, ids)); } catch (RuntimeException unavailable) { alert(id, "ACCEPTED_IDENTITY_REPAIR_UNAVAILABLE"); }
            var current = experiments.selectById(id);
            if (current.getEmergencyStopRequestedAt() != null) {
                String cancel = child(token, OnlineCapabilityPurpose.ONLINE_CANCEL, ids);
                try { requireSuccess(task.cancel(id.toString(), cancel, ids)); } catch (RuntimeException unavailable) { alert(id, "TASK_CANCEL_UNCONFIRMED"); }
                try { requireSuccess(agent.cancel(id.toString(), cancel, ids)); } catch (RuntimeException unavailable) { alert(id, "AGENT_CANCEL_UNCONFIRMED"); }
            }
            String observe = child(token, OnlineCapabilityPurpose.ONLINE_OBSERVE, ids);
            Map<String, OnlineTaskFactVO> taskFacts = Map.of(); Map<String, OnlineExecutionFactVO> agentFacts = Map.of();
            try { taskFacts = data(task.facts(id.toString(), observe, ids)).stream().collect(Collectors.toMap(OnlineTaskFactVO::taskId, value -> value)); }
            catch (RuntimeException unavailable) { alert(id, "TASK_FACT_UNAVAILABLE"); }
            try { agentFacts = data(agent.facts(id.toString(), observe, ids)).stream().collect(Collectors.toMap(value -> value.fact().taskId(), value -> value)); }
            catch (RuntimeException unavailable) { alert(id, "AGENT_FACT_UNAVAILABLE"); }
            for (var row : rows) {
                var fact = agentFacts.get(row.getTaskId().toString());
                try { authority.reconcile(id, row.getTaskId(), taskFacts.get(row.getTaskId().toString()), fact == null ? null : fact.fact()); }
                catch (RuntimeException inconsistent) { authority.safetyPause(id, OnlineReasonCode.ONLINE_FACT_UNKNOWN.name()); alert(id, "FACT_IDENTITY_CONFLICT"); }
            }
            cursors.put(id, rows.getLast().getId());
        } catch (RuntimeException unavailable) {
            authority.safetyPause(id, OnlineReasonCode.CANCEL_AUTHORIZATION_UNAVAILABLE.name());
            alert(id, "CONTROL_OR_OBSERVATION_UNAVAILABLE");
        }
    }

    private List<OnlineAssignmentEntity> page(Long id, Long cursor) {
        return assignments.selectList(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getExperimentId, id)
                .ne(OnlineAssignmentEntity::getSettlementStatus, "SETTLED").gt(OnlineAssignmentEntity::getId, cursor)
                .orderByAsc(OnlineAssignmentEntity::getId).last("LIMIT " + OnlineCapabilityConstant.MAX_TASK_COUNT));
    }
    private String renew(OnlineExperimentEntity experiment, String token) {
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw unavailable(); }
        var source = verifier.verify(token, OnlineCapabilityPurpose.ONLINE_CONTROL, experiment.getId().toString(), List.of());
        if (source.getExpiresAt().isAfter(Instant.now().plusSeconds(OnlineCapabilityConstant.RENEW_BEFORE_SECONDS))) { return token; }
        String next = child(token, OnlineCapabilityPurpose.ONLINE_CONTROL, List.of());
        var jwt = verifier.verify(next, OnlineCapabilityPurpose.ONLINE_CONTROL, experiment.getId().toString(), List.of());
        authority.renewedControl(experiment.getId(), experiment.getControlCiphertext(), experiment.getControlAuthorizedBy(), crypto.encrypt(next), LocalDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC));
        // CAS 失败表示人工授权已经更新，重新读取而不使用迟到的旧授权。
        return control.require(experiment.getId());
    }
    private String child(String token, OnlineCapabilityPurpose purpose, List<String> ids) { return data(auth.issue(token, new OnlineCapabilityIssueDTO(purpose, ids))); }
    private static <T> T data(Result<T> result) { requireSuccess(result); if (result.data() == null) { throw unavailable(); } return result.data(); }
    private static void requireSuccess(Result<?> result) { if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) { throw unavailable(); } }
    private static BusinessException unavailable() { return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, OnlineReasonCode.ONLINE_FACT_UNKNOWN.name()); }
    private void alert(Long id, String reason) {
        long now = System.nanoTime(); Long previous = alerts.get(id);
        if (previous == null || now - previous >= Duration.ofSeconds(UNRESOLVED_SECONDS).toNanos()) {
            alerts.put(id, now); log.warn("线上保护待对账 experimentId={} reason={}", id, reason);
        }
    }
}
