package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.utils.OnlineIdentityUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.common.enums.DocType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;

/** WAIT 只获不含正文的原动作证明，当前成员/文档归属与冻结版本缺失即拒绝。 */
@Service
@RequiredArgsConstructor
public class OnlineTaskPermissionService {
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;
    private final OnlineEvaluationFeign evaluation;
    private final SpacePermissionService permissions;
    private final DocumentService documents;
    private final TaskCapabilityVerifier taskVerifier;
    public List<String> actions(String taskId, String wait) {
        var verifier = verifiers.getIfAvailable();
        if (verifier == null) { throw denied(); }
        try { verifier.verifyWait(wait, taskId); } catch (RuntimeException invalid) { throw denied(); }
        var result = evaluation.waitBinding(taskId, wait);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw denied(); }
        var binding = result.data();
        return actions(binding);
    }
    public List<String> executionActions(String taskId, String capability) {
        var jwt = taskVerifier.verify(capability);
        if (!taskId.equals(jwt.getClaimAsString(JwtConstant.CLAIM_TASK_ID))) { throw denied(); }
        var result = evaluation.executionPermit(taskId, "Bearer " + capability);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw denied(); }
        var permit = result.data(); var binding = permit.binding();
        OnlineIdentityUtils.requireJwt(jwt, new OnlineDispatchIdentityDTO(binding.experimentId(), binding.assignmentId(), binding.schemaVersion(),
                permit.bindingHash(), permit.generation(), permit.permitHash()));
        return actions(binding);
    }
    private List<String> actions(OnlineTaskBindingDTO binding) {
        long spaceId = OnlineProtocolUtils.id(binding.spaceId());
        permissions.requireOnlineTaskCreatePermission(spaceId, OnlineProtocolUtils.id(binding.actorId()));
        var document = documents.requireDoc(OnlineProtocolUtils.id(binding.documentId()));
        if (!Long.valueOf(spaceId).equals(document.getSpaceId()) || !Integer.valueOf(DocStatus.NORMAL.getCode()).equals(document.getStatus())
                || DocType.fromCode(document.getDocType()) == null) { throw denied(); }
        documents.requireOnlineFrozenVersion(document.getId(), OnlineProtocolUtils.id(binding.documentVersion()), binding.documentContentHash());
        return document.getDocType().equals(DocType.DRAFT.getCode())
                ? List.of(JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_WRITE_DRAFT)
                : List.of(JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_CREATE_CHANGE_REQUEST);
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.RESOURCE_FORBIDDEN.name()); }
}
