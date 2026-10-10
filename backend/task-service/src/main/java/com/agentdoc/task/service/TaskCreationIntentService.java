package com.agentdoc.task.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;
import static com.agentdoc.task.enums.TaskCreationIntentStatus.*;
import static com.agentdoc.task.enums.TaskCreationIntentReason.*;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskCreationIntentMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** 同操作者/key先预留原身份；RPC、令牌签发和MQ均在持久化短事务之外。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskCreationIntentService {
    private final TaskCreationIntentMapper intents;
    private final TaskMapper tasks;
    private final TaskCreationIntentPersistenceService persistence;
    private final TaskMessagePublisher publisher;

    public TaskEntity create(TaskCreateDTO request, Long actor, Function<Long, TaskEntity> prepare,
            Consumer<TaskEntity> authorize, Function<TaskEntity, String> encryptedCapability) {
        return create(request, actor, prepare, authorize, encryptedCapability, null);
    }

    public TaskEntity create(TaskCreateDTO request, Long actor, Function<Long, TaskEntity> prepare,
            Consumer<TaskEntity> authorize, Function<TaskEntity, String> encryptedCapability,
            Function<TaskCreationIntentEntity, OnlineRouteVO> route) {
        String hash = requestHash(request, actor);
        var intent = prior(request.spaceId(), actor, request.clientRequestKey());
        if (intent == null) {
            intent = new TaskCreationIntentEntity(); intent.setId(IdWorker.getId()); intent.setTaskId(IdWorker.getId());
            intent.setSpaceId(request.spaceId()); intent.setActorId(actor); intent.setRequestKey(request.clientRequestKey());
            intent.setRequestHash(hash); intent.setStatus(PREPARING.name());
            try { persistence.reserve(intent); }
            catch (DuplicateKeyException race) { intent = prior(request.spaceId(), actor, request.clientRequestKey()); if (intent == null) { throw race; } }
        }
        if (!hash.equals(intent.getRequestHash())) { throw new BusinessException(ErrorCode.CONFLICT, IDEMPOTENCY_CONFLICT.name()); }
        TaskEntity task = intent.getInputJson() == null ? persistence.freeze(intent.getId(), prepare.apply(intent.getTaskId()))
                : TaskCreationIntentPersistenceService.frozen(intent);
        // 每次重试复核当前权限、资源归属与冻结版本；不重新计算输入/预算。
        authorize.accept(task);
        intent = intents.selectById(intent.getId());
        if (route != null && intent.getBindingJson() == null && FROZEN.name().equals(intent.getStatus())) {
            task = persistence.route(intent.getId(), route.apply(intent));
        }
        if (PUBLISHED.name().equals(intent.getStatus())) {
            task = tasks.selectById(intent.getTaskId());
            if (task == null) { throw new BusinessException(ErrorCode.CONFLICT, MANIFEST_INVALID.name()); }
            return task;
        }
        task = persistence.persist(intent.getId());
        if (!Integer.valueOf(TaskStatus.PENDING.getCode()).equals(task.getStatus())) { return task; }
        String reason = TASK_CAPABILITY_PENDING.name();
        try {
            boolean online = task.getOnlineAssignmentId() != null;
            String stored = online ? task.getOnlineWaitCapability() : task.getCapabilityToken();
            if (stored == null || stored.isBlank()) {
                String encrypted = encryptedCapability.apply(task);
                if (encrypted == null || encrypted.isBlank()) { throw new BusinessException(ErrorCode.CONFLICT, MANIFEST_INVALID.name()); }
                var update = new LambdaUpdateWrapper<TaskEntity>().eq(TaskEntity::getId, task.getId())
                        .eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode());
                if (online) { update.isNull(TaskEntity::getOnlineWaitCapability).set(TaskEntity::getOnlineWaitCapability, encrypted); }
                else { update.isNull(TaskEntity::getCapabilityToken).set(TaskEntity::getCapabilityToken, encrypted); }
                tasks.update(null, update);
                task = tasks.selectById(task.getId());
                if (task != null && !Integer.valueOf(TaskStatus.PENDING.getCode()).equals(task.getStatus())) { return task; }
                if (task == null || (online ? task.getOnlineWaitCapability() : task.getCapabilityToken()) == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, MANIFEST_INVALID.name());
                }
            }
            reason = TASK_MESSAGE_PENDING.name();
            // 多个调用并发发布原Task是允许的；消费者既有Task状态/A2A幂等负责去重。
            publisher.publish(task.getId()); persistence.published(intent.getId());
            var current = tasks.selectById(task.getId());
            if (current == null) { throw new BusinessException(ErrorCode.CONFLICT, MANIFEST_INVALID.name()); }
            return current;
        } catch (RuntimeException failure) {
            persistence.failed(intent.getId(), reason);
            log.warn("Task创建补偿待重试 taskId={} reason={} failureType={}", intent.getTaskId(), reason, failure.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, reason);
        }
    }

    static String requestHash(TaskCreateDTO request, Long actor) {
        if (request == null || actor == null || actor <= 0 || request.spaceId() == null || request.spaceId() <= 0
                || request.agentId() == null || request.agentId() <= 0 || request.documentId() == null || request.documentId() <= 0
                || request.name() == null || request.instruction() == null || request.name().isBlank() || request.instruction().isBlank()
                || request.clientRequestKey() == null || !request.clientRequestKey().matches("[A-Za-z0-9._:-]{1,64}")
                || request.tokenBudget() != null && request.tokenBudget() <= 0) { throw new BusinessException(ErrorCode.BAD_REQUEST, REQUEST_KEY_REQUIRED.name()); }
        Map<String, Object> payload = new TreeMap<>(); payload.put("actorId", actor.toString()); payload.put("spaceId", request.spaceId().toString());
        payload.put("agentId", request.agentId().toString()); payload.put("documentId", request.documentId().toString());
        payload.put("name", request.name().trim()); payload.put("instruction", request.instruction().trim());
        payload.put("tokenBudget", request.tokenBudget() == null ? null : request.tokenBudget().toString());
        payload.put("readScope", request.readScope() == null ? TaskReadScope.FULL.name() : request.readScope().name());
        payload.put("focusRegions", request.focusRegions() == null ? List.of() : request.focusRegions());
        return OnlineProtocolUtils.hash("online.task-request", payload);
    }

    private TaskCreationIntentEntity prior(Long space, Long actor, String key) {
        return intents.selectOne(new LambdaQueryWrapper<TaskCreationIntentEntity>().eq(TaskCreationIntentEntity::getSpaceId, space)
                .eq(TaskCreationIntentEntity::getActorId, actor).eq(TaskCreationIntentEntity::getRequestKey, key));
    }
}
