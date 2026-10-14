package com.agentdoc.evaluation.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static com.agentdoc.common.enums.OnlineReasonCode.BINDING_INVALID;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineAuthorizationProofVO;
import com.agentdoc.common.feign.vo.OnlineResourceScopeVO;
import java.util.ArrayList;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 权威身份与绑定只读证明；不暴露分配、占槽或执行入口。 */
@Service
@RequiredArgsConstructor
public class OnlineAuthorizationProofService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final OnlineExecutionAuthority authority;
    private final SpaceAccessService access;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public OnlineAuthorizationProofVO human(String experimentId) {
        AuthUtils.getUserIdOrException();
        var experiment = experiment(experimentId);
        access.requireOwner(experiment.getSpaceId());
        return projection(experiment);
    }

    public OnlineAuthorizationProofVO control(String experimentId, String token) {
        return projection(controlled(experimentId, token));
    }
    public OnlineResourceScopeVO resourceScope(String experimentId, String token) {
        var experiment = controlled(experimentId, token); var manifest = authority.manifest(experiment);
        List<String> ids = new ArrayList<>(); manifest.path("documentIds").forEach(id -> ids.add(id.asText()));
        return new OnlineResourceScopeVO(experiment.getSpaceId().toString(), experiment.getAgentId().toString(), List.copyOf(ids), experiment.getEmergencyStopRequestedAt() != null);
    }

    public List<OnlineTaskBindingDTO> bindings(String experimentId, String token, List<String> rawIds) {
        var experiment = controlled(experimentId, token);
        return bindings(experiment, rawIds);
    }

    public List<OnlineTaskBindingDTO> scopedBindings(String experimentId, String token, OnlineCapabilityPurpose purpose, List<String> rawIds) {
        if (purpose == null || purpose == OnlineCapabilityPurpose.ONLINE_CONTROL) { throw denied(); }
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        var ids = OnlineCapabilityUtils.taskIds(rawIds);
        Jwt proof;
        try { proof = verifier.verify(token, purpose, experimentId, ids); } catch (JwtException invalid) { throw denied(); }
        var experiment = experiment(experimentId); requireAuthority(experiment, proof);
        return bindings(experiment, ids);
    }

    private List<OnlineTaskBindingDTO> bindings(OnlineExperimentEntity experiment, List<String> rawIds) {
        var ids = OnlineCapabilityUtils.taskIds(rawIds);
        Map<Long, OnlineAssignmentEntity> values = assignments.selectList(new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experiment.getId())
                .in(OnlineAssignmentEntity::getTaskId, ids.stream().map(OnlineProtocolUtils::id).toList()))
                .stream().collect(Collectors.toMap(OnlineAssignmentEntity::getTaskId, value -> value));
        if (values.size() != ids.size()) { throw denied(); }
        return ids.stream().map(id -> {
            var binding = authority.binding(values.get(OnlineProtocolUtils.id(id)));
            if (!experiment.getManifestHash().equals(binding.manifestHash())) { throw denied(); }
            return binding;
        }).toList();
    }

    private OnlineExperimentEntity controlled(String experimentId, String token) {
        var verifier = verifiers.getIfAvailable();
        if (verifier == null) { throw denied(); }
        Jwt proof;
        try { proof = verifier.verify(token, OnlineCapabilityPurpose.ONLINE_CONTROL, experimentId, List.of()); }
        catch (JwtException invalid) { throw denied(); }
        var experiment = experiment(experimentId);
        requireAuthority(experiment, proof);
        return experiment;
    }

    private void requireAuthority(OnlineExperimentEntity experiment, Jwt proof) {
        if (!experiment.getSpaceId().toString().equals(proof.getClaimAsString(JwtConstant.CLAIM_SPACE_ID))
                || !experiment.getManifestHash().equals(proof.getClaimAsString(MANIFEST_HASH))
                || experiment.getControlAuthorizedBy() == null || !experiment.getControlAuthorizedBy().toString()
                .equals(proof.getClaimAsString(AUTHORIZED_BY))) { throw denied(); }
    }

    private OnlineExperimentEntity experiment(String id) {
        var value = experiments.selectById(OnlineProtocolUtils.id(id));
        if (value == null || !Objects.equals(value.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION)) { throw denied(); }
        authority.manifest(value);
        return value;
    }

    private OnlineAuthorizationProofVO projection(OnlineExperimentEntity value) {
        return new OnlineAuthorizationProofVO(value.getId().toString(), value.getSpaceId().toString(), value.getManifestHash(),
                value.getManifestSchemaVersion(), value.getStatus(),
                value.getControlAuthorizedBy() == null ? null : value.getControlAuthorizedBy().toString());
    }
    private BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, BINDING_INVALID.name()); }
}
