package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAuthFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineExecutionFactVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.execution.TaskExecutionService;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TaskCreationIntentMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static com.agentdoc.common.constant.OnlineCapabilityConstant.AUTHORIZED_BY;

/** 原接受身份修复；CONTROL 不签发 WAIT/执行能力，不重建或恢复被删除的历史 Task。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskOnlineCompensationService {
    private final TaskMapper tasks;
    private final TaskCreationIntentMapper intents;
    private final TaskCreationIntentPersistenceService persistence;
    private final TaskMessagePublisher publisher;
    private final TaskExecutionService dispatch;
    private final TokenUsageService tokenUsage;
    private final OnlineEvaluationFeign evaluation;
    private final OnlineAuthFeign auth;
    private final OnlineAgentFeign agent;
    private final OnlineDocumentFeign document;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public void repair(String experimentId, String token, List<String> rawIds) {
        var ids = OnlineCapabilityUtils.taskIds(rawIds); var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        var proof = verifier.verify(token, OnlineCapabilityPurpose.ONLINE_CONTROL, experimentId, List.of());
        var bindings = data(evaluation.bindings(experimentId, token, ids));
        if (!ids.equals(OnlineCapabilityUtils.taskIds(bindings.stream().map(OnlineTaskBindingDTO::taskId).toList()))) { throw denied(); }
        var permitted = document.requireProtectionPermission(experimentId, token); if (permitted == null || permitted.code() != ErrorCode.SUCCESS.getCode()) { throw denied(); }
        var scope = data(evaluation.resourceScope(experimentId, token));
        var prepared = intents.selectList(new LambdaQueryWrapper<TaskCreationIntentEntity>().in(TaskCreationIntentEntity::getTaskId,
                ids.stream().map(OnlineProtocolUtils::id).toList())).stream().collect(Collectors.toMap(TaskCreationIntentEntity::getTaskId, value -> value));
        String observation = data(auth.issue(token, new OnlineCapabilityIssueDTO(OnlineCapabilityPurpose.ONLINE_OBSERVE, ids)));
        Map<String, OnlineExecutionFactVO> facts = data(agent.facts(experimentId, observation, ids)).stream()
                .collect(Collectors.toMap(value -> value.fact().taskId(), value -> value));
        if (!ids.equals(OnlineCapabilityUtils.taskIds(facts.keySet().stream().toList()))) { throw denied(); }
        Long authorizedBy = OnlineProtocolUtils.id(proof.getClaimAsString(AUTHORIZED_BY));
        for (var binding : bindings) {
            try {
                var intent = prepared.get(OnlineProtocolUtils.id(binding.taskId())); if (intent == null) { throw denied(); }
                var current = persistence.repairAccepted(intent.getId(), binding, authorizedBy, scope.emergency());
                var remote = facts.get(binding.taskId());
                if (!OnlineProtocolUtils.hash("online.binding", binding).equals(remote.fact().bindingHash())) { throw denied(); }
                if (remote.fact().executionId() != null) { restoreRemote(current, binding, remote); }
                current = tasks.selectById(current.getId());
                if (List.of("COMPLETED", "FAILED", "CANCELED", "TIMED_OUT").contains(remote.fact().executionStatus()) && remote.tokenUsage() != null) {
                    var usage = remote.tokenUsage();
                    if (!remote.fact().executionId().equals(String.valueOf(usage.executionId()))) { throw denied(); }
                    tokenUsage.recordRemote(current, new A2aTokenUsage(usage.inputTokens(), usage.cachedInputTokens(), usage.outputTokens(),
                            Boolean.TRUE.equals(usage.inputTokensEstimated()), Boolean.TRUE.equals(usage.cachedInputTokensEstimated()), Boolean.TRUE.equals(usage.outputTokensEstimated()),
                            usage.executionId(), usage.modelId(), usage.modelConfigVersion(), usage.inputPricePerMillion(), usage.outputPricePerMillion(),
                            usage.currency(), usage.pricingSchemaVersion(), usage.pricingCapturedAt()));
                }
                if (!scope.emergency() && TaskStatus.PENDING.getCodeEquals(current.getStatus()) && (current.getOnlineWaitCapability() != null || current.getCapabilityToken() != null)) {
                    publisher.publish(current.getId());
                } else if (!scope.emergency() && "ABSENT".equals(remote.fact().executionStatus()) && TaskStatus.DISPATCHED.getCodeEquals(current.getStatus())
                        && current.getA2aTaskId() == null) { dispatch.repairOnlineDispatch(current.getId()); }
            } catch (RuntimeException unavailable) {
                log.warn("原线上身份补偿未完成 experimentId={} taskId={} failureType={}", experimentId, binding.taskId(), unavailable.getClass().getSimpleName());
            }
        }
    }
    private void restoreRemote(TaskEntity task, OnlineTaskBindingDTO binding, OnlineExecutionFactVO remote) {
        Long executionId = OnlineProtocolUtils.id(remote.fact().executionId());
        if (remote.a2aTaskId() == null || remote.a2aContextId() == null || task.getAgentExecutionId() != null && !executionId.equals(task.getAgentExecutionId())
                || task.getA2aTaskId() != null && !remote.a2aTaskId().equals(task.getA2aTaskId())
                || task.getA2aContextId() != null && !remote.a2aContextId().equals(task.getA2aContextId())) { throw denied(); }
        TaskOnlineObservationService.requireIdentity(task, binding, remote.fact().bindingHash());
        tasks.update(null, new LambdaUpdateWrapper<TaskEntity>().eq(TaskEntity::getId, task.getId()).eq(TaskEntity::getOnlineBindingHash, remote.fact().bindingHash())
                .and(update -> update.isNull(TaskEntity::getA2aTaskId).or().eq(TaskEntity::getA2aTaskId, remote.a2aTaskId()))
                .and(update -> update.isNull(TaskEntity::getAgentExecutionId).or().eq(TaskEntity::getAgentExecutionId, executionId))
                .set(TaskEntity::getA2aTaskId, remote.a2aTaskId()).set(TaskEntity::getA2aContextId, remote.a2aContextId())
                .set(TaskEntity::getAgentExecutionId, executionId));
    }
    private static <T> T data(Result<T> result) { if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw denied(); } return result.data(); }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
