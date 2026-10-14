package com.agentdoc.task.service;

import static com.agentdoc.task.enums.TaskCreationIntentStatus.*;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.task.enums.TaskStatus;
import java.time.LocalDateTime;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.mapper.TaskCreationIntentMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 仅做意图/Task/创建审计的短事务，禁止在持锁期间签发令牌或调用MQ。 */
@Service
@RequiredArgsConstructor
public class TaskCreationIntentPersistenceService {
    private final TaskCreationIntentMapper intents;
    private final TaskMapper tasks;
    private final AuditLogService audit;

    @Transactional
    public void reserve(TaskCreationIntentEntity intent) { intents.insert(intent); }

    @Transactional
    public TaskEntity route(Long intentId, OnlineRouteVO decision) {
        var intent = require(intentId);
        if (intent.getBindingJson() != null) { return frozen(intent); }
        if (!FROZEN.name().equals(intent.getStatus()) || decision == null
                || (decision.binding() == null) == (decision.reason() == null)) { throw invalid(); }
        if (decision.binding() != null) {
            var request = TaskOnlineRoutingService.request(intent);
            var binding = decision.binding();
            if (!request.taskId().equals(binding.taskId()) || !request.spaceId().equals(binding.spaceId())
                    || !request.agentId().equals(binding.agentId()) || !request.documentId().equals(binding.documentId())
                    || !request.actorId().equals(binding.actorId()) || !Objects.equals(request.tokenBudget(), binding.tokenBudget())
                    || !request.inputHash().equals(binding.inputHash()) || !request.documentVersion().equals(binding.documentVersion())
                    || !request.documentContentHash().equals(binding.documentContentHash())
                    || request.inputSchemaVersion() != binding.inputSchemaVersion()
                    || binding.schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION
                    || !"LIVE".equals(binding.executionMode()) || !"ORIGINAL".equals(binding.lineageType())) { throw invalid(); }
        }
        intent.setBindingJson(OnlineProtocolUtils.canonical("online.route", decision)); intents.updateById(intent);
        return frozen(intent);
    }

    @Transactional
    public TaskEntity freeze(Long intentId, TaskEntity prepared) {
        var intent = require(intentId);
        if (intent.getInputJson() != null) { return frozen(intent); }
        if (!PREPARING.name().equals(intent.getStatus()) || !Objects.equals(intent.getTaskId(), prepared.getId())
                || !Objects.equals(intent.getSpaceId(), prepared.getSpaceId()) || !Objects.equals(intent.getActorId(), prepared.getCreatedBy())
                || prepared.getCapabilityToken() != null || prepared.getCreatedAt() != null) { throw invalid(); }
        intent.setInputJson(JsonUtils.toJson(prepared)); intent.setEffectiveBudget(prepared.getTokenBudget()); intent.setStatus(FROZEN.name());
        intents.updateById(intent); return prepared;
    }

    @Transactional
    public TaskEntity persist(Long intentId) {
        var intent = require(intentId);
        var existing = tasks.selectById(intent.getTaskId());
        if (existing != null) { return existing; }
        if (!FROZEN.name().equals(intent.getStatus())) { throw invalid(); }
        var task = frozen(intent); tasks.insert(task);
        audit.recordHuman(task.getSpaceId(), AuditAction.TASK_CREATED, AuditTargetType.TASK, task.getId(), null);
        intent.setStatus(CREATED.name()); intents.updateById(intent); return task;
    }

    /** 仅修复已接受原意图的缺失记录；历史已创建记录被删除时不能复活。 */
    @Transactional
    public TaskEntity repairAccepted(Long intentId, OnlineTaskBindingDTO binding, Long authorizedBy, boolean emergency) {
        var intent = require(intentId); var existing = tasks.selectById(intent.getTaskId());
        if (existing != null) { TaskOnlineObservationService.requireIdentity(existing, binding, OnlineProtocolUtils.hash("online.binding", binding)); return existing; }
        if (!FROZEN.name().equals(intent.getStatus())) { throw invalid(); }
        route(intentId, new OnlineRouteVO(binding, null));
        var task = frozen(require(intentId)); TaskOnlineObservationService.requireIdentity(task, binding, OnlineProtocolUtils.hash("online.binding", binding));
        if (emergency) { task.setStatus(TaskStatus.TERMINATED.getCode()); task.setEndTime(LocalDateTime.now()); }
        tasks.insert(task); audit.recordOnlineCompensation(task.getSpaceId(), task.getId(), authorizedBy, task.getOnlineBindingHash());
        intent.setStatus(CREATED.name()); intents.updateById(intent); return task;
    }

    @Transactional
    public void published(Long intentId) {
        var intent = require(intentId);
        if (PUBLISHED.name().equals(intent.getStatus())) { return; }
        if (!CREATED.name().equals(intent.getStatus())) { throw invalid(); }
        intents.update(null, new LambdaUpdateWrapper<TaskCreationIntentEntity>().eq(TaskCreationIntentEntity::getId, intentId)
                .set(TaskCreationIntentEntity::getStatus, PUBLISHED.name()).set(TaskCreationIntentEntity::getReasonCode, null));
    }

    public void failed(Long intentId, String reason) {
        intents.update(null, new LambdaUpdateWrapper<TaskCreationIntentEntity>().eq(TaskCreationIntentEntity::getId, intentId)
                .ne(TaskCreationIntentEntity::getStatus, PUBLISHED.name()).set(TaskCreationIntentEntity::getReasonCode, reason));
    }

    public static TaskEntity frozen(TaskCreationIntentEntity intent) {
        var task = JsonUtils.parseStrict(intent.getInputJson(), TaskEntity.class);
        if (task == null || !Objects.equals(task.getId(), intent.getTaskId()) || !Objects.equals(task.getSpaceId(), intent.getSpaceId())
                || !Objects.equals(task.getCreatedBy(), intent.getActorId()) || !Objects.equals(task.getTokenBudget(), intent.getEffectiveBudget())
                || task.getCapabilityToken() != null) { throw invalid(); }
        if (intent.getBindingJson() != null) {
            var envelope = OnlineProtocolUtils.object(intent.getBindingJson());
            var route = JsonUtils.parseStrict(JsonUtils.toJson(envelope.get("payload")), OnlineRouteVO.class);
            if (route == null || !OnlineProtocolUtils.canonical("online.route", route).equals(intent.getBindingJson())
                    || (route.binding() == null) == (route.reason() == null)) { throw invalid(); }
            var binding = route.binding();
            if (binding != null) {
                task.setOnlineExperimentId(OnlineProtocolUtils.id(binding.experimentId()));
                task.setOnlineAssignmentId(OnlineProtocolUtils.id(binding.assignmentId()));
                task.setOnlineBindingSchemaVersion(binding.schemaVersion());
                task.setOnlineBindingHash(OnlineProtocolUtils.hash("online.binding", binding));
            }
        }
        return task;
    }
    private TaskCreationIntentEntity require(Long id) {
        var value = intents.lock(id); if (value == null) { throw invalid(); } return value;
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, OnlineReasonCode.MANIFEST_INVALID.name()); }
}
