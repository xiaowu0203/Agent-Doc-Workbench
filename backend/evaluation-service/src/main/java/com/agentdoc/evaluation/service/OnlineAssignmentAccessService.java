package com.agentdoc.evaluation.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.utils.OnlineIdentityUtils;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExecutionSlotMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExecutionSlotEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/** 绑定/槽证明只从本领域权威行生成；不把 WAIT 升为通用身份。 */
@Service
@RequiredArgsConstructor
public class OnlineAssignmentAccessService {
    private final OnlineAssignmentMapper assignments;
    private final OnlineExecutionSlotMapper slots;
    private final OnlineExperimentMapper experiments;
    private final OnlineExecutionAuthority authority;
    private final OnlineTaskFeign task;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;
    private final OnlineControlAccessService control;

    public OnlineTaskBindingDTO humanBinding(String taskId) {
        var row = row(taskId);
        if (!Objects.equals(row.getCreatedBy(), AuthUtils.getUserIdOrException())) { throw denied(); }
        return authority.binding(row);
    }
    public OnlineTaskBindingDTO waitBinding(String taskId, String wait) {
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        var jwt = verifier.verifyWait(wait, taskId);
        var binding = authority.binding(row(taskId));
        if (!binding.experimentId().equals(jwt.getClaimAsString(EXPERIMENT_ID))
                || !binding.assignmentId().equals(jwt.getClaimAsString(ASSIGNMENT_ID))
                || !binding.spaceId().equals(jwt.getClaimAsString(JwtConstant.CLAIM_SPACE_ID))
                || !binding.manifestHash().equals(jwt.getClaimAsString(MANIFEST_HASH))
                || !OnlineProtocolUtils.hash("online.binding", binding).equals(jwt.getClaimAsString(BINDING_HASH))) { throw denied(); }
        var experiment = experiments.selectById(OnlineProtocolUtils.id(binding.experimentId()));
        if (experiment == null || !binding.manifestHash().equals(experiment.getManifestHash())) { throw denied(); }
        authority.manifest(experiment); return binding;
    }
    public OnlineSlotPermitVO claim(String taskId, String wait) {
        var binding = waitBinding(taskId, wait);
        control.require(OnlineProtocolUtils.id(binding.experimentId()));
        var proof = task.waitDispatchProof(taskId, wait);
        if (proof == null || proof.code() != ErrorCode.SUCCESS.getCode() || proof.data() == null
                || !binding.equals(proof.data().binding()) || !"PENDING".equals(proof.data().taskStatus())) { throw denied(); }
        return authority.claim(OnlineProtocolUtils.id(binding.experimentId()), OnlineProtocolUtils.id(taskId), OnlineProtocolUtils.hash("online.binding", binding));
    }
    public OnlineSlotPermitVO permit(String taskId, String wait) {
        var binding = waitBinding(taskId, wait);
        control.require(OnlineProtocolUtils.id(binding.experimentId()));
        return currentPermit(binding);
    }
    public OnlineTaskBindingDTO waiting(String taskId, String wait) {
        var binding = waitBinding(taskId, wait); control.require(OnlineProtocolUtils.id(binding.experimentId()));
        var experiment = experiments.selectById(OnlineProtocolUtils.id(binding.experimentId()));
        if (experiment.getEmergencyStopRequestedAt() != null || "STOPPED".equals(experiment.getStatus())) { throw denied(); }
        return binding;
    }
    public OnlineSlotPermitVO executionPermit(String taskId) {
        Jwt jwt = AuthUtils.currentJwt();
        if (jwt == null || !AuthUtils.isAgent() || !taskId.equals(jwt.getClaimAsString(JwtConstant.CLAIM_TASK_ID))) { throw denied(); }
        var permit = currentPermit(authority.binding(row(taskId)));
        control.require(OnlineProtocolUtils.id(permit.binding().experimentId()));
        var binding = permit.binding();
        OnlineIdentityUtils.requireJwt(jwt, new OnlineDispatchIdentityDTO(binding.experimentId(), binding.assignmentId(), binding.schemaVersion(),
                permit.bindingHash(), permit.generation(), permit.permitHash()));
        return permit;
    }
    public OnlineSlotPermitVO begin(String taskId) {
        var permit = executionPermit(taskId);
        return authority.begin(OnlineProtocolUtils.id(permit.binding().experimentId()), OnlineProtocolUtils.id(taskId), permit.generation(), permit.permitHash());
    }
    private OnlineSlotPermitVO currentPermit(OnlineTaskBindingDTO binding) {
        String taskId = binding.taskId();
        var slot = slots.selectOne(new LambdaQueryWrapper<OnlineExecutionSlotEntity>()
                .eq(OnlineExecutionSlotEntity::getTaskId, OnlineProtocolUtils.id(taskId)));
        var experiment = experiments.selectById(OnlineProtocolUtils.id(binding.experimentId()));
        String hash = OnlineProtocolUtils.hash("online.binding", binding);
        if (slot == null || slot.getGeneration() <= 0 || !hash.equals(slot.getBindingHash()) || experiment == null || experiment.getEmergencyStopRequestedAt() != null
                || "STOPPED".equals(experiment.getStatus())) { throw denied(); }
        String permit = OnlineProtocolUtils.hash("online.slot-permit", Map.of("experimentId", binding.experimentId(),
                "assignmentId", binding.assignmentId(), "taskId", binding.taskId(), "bindingHash", hash, "generation", slot.getGeneration().toString()));
        return new OnlineSlotPermitVO(binding, hash, slot.getGeneration(), permit, slot.getStartedAt() != null);
    }
    private OnlineAssignmentEntity row(String taskId) {
        var row = assignments.selectOne(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getTaskId, OnlineProtocolUtils.id(taskId)));
        if (row == null) { throw denied(); } return row;
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
