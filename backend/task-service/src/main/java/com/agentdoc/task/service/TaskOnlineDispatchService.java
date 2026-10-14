package com.agentdoc.task.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAuthFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.feign.vo.OnlineTaskDispatchProofVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.OnlineIdentityUtils;
import com.agentdoc.common.utils.OnlineReleaseUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskCreationIntentMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.time.Instant;

/** 原 Task 先取槽再交换执行凭证；WAIT 从不写入普通 capability_token。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineDispatchService {
    private final TaskMapper tasks;
    private final TaskCreationIntentMapper intents;
    private final OnlineAuthFeign auth;
    private final OnlineEvaluationFeign evaluation;
    private final OnlineDocumentFeign document;
    private final TaskCapabilityCryptoService crypto;
    private final TaskCapabilityVerifier taskVerifier;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public String initialWait(TaskEntity task) { return crypto.encrypt(data(auth.initialWait(task.getId().toString()))); }

    /** 续期只能以仍有效的原 WAIT 为来源，丢失/过期不由 CONTROL 代替用户签发模型权限。 */
    public void renewWait(TaskEntity task) {
        if (task.getCapabilityToken() != null || task.getOnlineWaitCapability() == null || !TaskStatus.PENDING.getCodeEquals(task.getStatus())) { return; }
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        String encrypted = task.getOnlineWaitCapability(); String source = crypto.decrypt(encrypted);
        var jwt = verifier.verifyWait(source, task.getId().toString());
        if (jwt.getExpiresAt().isAfter(Instant.now().plusSeconds(RENEW_BEFORE_SECONDS))) { return; }
        String next = data(auth.renewWait(task.getId().toString(), source)); verifier.verifyWait(next, task.getId().toString());
        tasks.update(null, new LambdaUpdateWrapper<TaskEntity>().eq(TaskEntity::getId, task.getId()).eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                .isNull(TaskEntity::getCapabilityToken).apply("BINARY online_wait_capability = {0}", encrypted)
                .set(TaskEntity::getOnlineWaitCapability, crypto.encrypt(next)));
    }

    public void requireCurrentResourceAccess(TaskEntity task, String capability) {
        if (task.getOnlineAssignmentId() == null) { return; }
        if (!originalActions(task).equals(data(document.executionActions(task.getId().toString(), "Bearer " + capability)))) { throw denied(); }
    }

    public OnlineTaskDispatchProofVO humanProof(TaskEntity task) {
        var binding = data(evaluation.humanBinding(task.getId().toString()));
        return proof(task, binding, originalActions(task));
    }

    public OnlineTaskDispatchProofVO waitProof(String taskId, String wait) {
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        var jwt = verifier.verifyWait(wait, taskId);
        var task = tasks.selectById(OnlineProtocolUtils.id(taskId));
        if (task == null || task.getOnlineBindingHash() == null || !task.getOnlineBindingHash().equals(jwt.getClaimAsString(BINDING_HASH))
                || !task.getOnlineExperimentId().toString().equals(jwt.getClaimAsString(EXPERIMENT_ID))
                || !task.getOnlineAssignmentId().toString().equals(jwt.getClaimAsString(ASSIGNMENT_ID))) { throw denied(); }
        var actions = data(document.waitActions(taskId, wait));
        if (!originalActions(task).equals(actions)) { throw denied(); }
        return proof(task, data(evaluation.waitBinding(taskId, wait)), actions);
    }

    public boolean prepareDispatch(TaskEntity task) {
        if (task.getOnlineAssignmentId() == null) { return true; }
        renewWait(task);
        task = tasks.selectById(task.getId());
        if (task == null) { throw denied(); }
        if (task.getCapabilityToken() != null) {
            var jwt = taskVerifier.verify(crypto.decrypt(task.getCapabilityToken()));
            OnlineIdentityUtils.requireJwt(jwt, identity(task));
            var current = data(evaluation.executionPermit(task.getId().toString(), "Bearer " + jwt.getTokenValue()));
            if (!task.getOnlineBindingHash().equals(current.bindingHash()) || !task.getOnlineSlotGeneration().equals(current.generation())
                    || !task.getOnlineSlotPermitHash().equals(current.permitHash())) { throw denied(); }
            return true;
        }
        String wait = crypto.decrypt(task.getOnlineWaitCapability());
        var permitResult = evaluation.claim(task.getId().toString(), wait);
        if (permitResult != null && permitResult.code() != ErrorCode.SUCCESS.getCode()
                && OnlineReasonCode.ONLINE_SLOT_BUSY.name().equals(permitResult.message())) { return false; }
        var permit = data(permitResult);
        if (!Objects.equals(task.getOnlineBindingHash(), permit.bindingHash())) { throw denied(); }
        String capability = data(auth.exchange(task.getId().toString(), wait));
        var identity = new OnlineDispatchIdentityDTO(task.getOnlineExperimentId().toString(), task.getOnlineAssignmentId().toString(),
                task.getOnlineBindingSchemaVersion(), task.getOnlineBindingHash(), permit.generation(), permit.permitHash());
        OnlineIdentityUtils.requireJwt(taskVerifier.verify(capability), identity);
        tasks.update(null, new LambdaUpdateWrapper<TaskEntity>().eq(TaskEntity::getId, task.getId()).isNull(TaskEntity::getCapabilityToken)
                .eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode()).set(TaskEntity::getCapabilityToken, crypto.encrypt(capability))
                .set(TaskEntity::getOnlineSlotGeneration, permit.generation()).set(TaskEntity::getOnlineSlotPermitHash, permit.permitHash()));
        var current = tasks.selectById(task.getId());
        if (current == null || !TaskStatus.PENDING.getCodeEquals(current.getStatus())) { return false; }
        if (current.getCapabilityToken() == null) { throw denied(); }
        OnlineIdentityUtils.requireJwt(taskVerifier.verify(crypto.decrypt(current.getCapabilityToken())), identity(current)); return true;
    }

    private OnlineTaskDispatchProofVO proof(TaskEntity task, OnlineTaskBindingDTO binding, List<String> actions) {
        TaskOnlineObservationService.requireIdentity(task, binding, OnlineProtocolUtils.hash("online.binding", binding));
        var intent = intents.selectOne(new LambdaQueryWrapper<TaskCreationIntentEntity>().eq(TaskCreationIntentEntity::getTaskId, task.getId()));
        if (intent == null || intent.getBindingJson() == null) { throw denied(); }
        var route = JsonUtils.parseStrict(JsonUtils.toJson(OnlineProtocolUtils.object(intent.getBindingJson()).get("payload")), OnlineRouteVO.class);
        if (route == null || !binding.equals(route.binding()) || !OnlineProtocolUtils.canonical("online.route", route).equals(intent.getBindingJson())
                || !task.getOnlineBindingHash().equals(OnlineProtocolUtils.hash("online.binding", binding))
                || !Objects.equals(task.getOnlineAssignmentId(), OnlineProtocolUtils.id(binding.assignmentId()))
                || !Objects.equals(task.getOnlineExperimentId(), OnlineProtocolUtils.id(binding.experimentId()))) { throw denied(); }
        return new OnlineTaskDispatchProofVO(TaskOnlineRoutingService.request(intent), binding, TaskStatus.fromCode(task.getStatus()).name(),
                actions, OnlineReleaseUtils.current());
    }
    public static OnlineDispatchIdentityDTO identity(TaskEntity task) {
        if (task.getOnlineAssignmentId() == null && task.getOnlineExperimentId() == null && task.getOnlineBindingSchemaVersion() == null
                && task.getOnlineBindingHash() == null && task.getOnlineSlotGeneration() == null && task.getOnlineSlotPermitHash() == null) { return null; }
        return new OnlineDispatchIdentityDTO(task.getOnlineExperimentId() == null ? null : task.getOnlineExperimentId().toString(),
                task.getOnlineAssignmentId() == null ? null : task.getOnlineAssignmentId().toString(), task.getOnlineBindingSchemaVersion(),
                task.getOnlineBindingHash(), task.getOnlineSlotGeneration(), task.getOnlineSlotPermitHash());
    }
    private static List<String> originalActions(TaskEntity task) {
        if (DocType.fromCode(task.getDocumentType()) == null) { throw denied(); }
        return Integer.valueOf(DocType.DRAFT.getCode()).equals(task.getDocumentType()) ? List.of(JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_WRITE_DRAFT)
                : List.of(JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_CREATE_CHANGE_REQUEST);
    }
    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, result == null ? OnlineReasonCode.ONLINE_FACT_UNKNOWN.name() : result.message());
        }
        return result.data();
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
