package com.agentdoc.agent.service;

import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.utils.OnlineIdentityUtils;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Set;

/** 接受与真正开始分别复核权威槽；缺失或门禁失效不能回退普通运行。 */
@Service
@RequiredArgsConstructor
public class OnlineAgentAdmissionService {
    private final SecurityVerifyProperties properties;
    private final OnlineEvaluationFeign evaluation;
    private final OnlineTaskFeign tasks;
    private final TaskCapabilityVerifier verifier;
    private final AgentExecutionMapper executions;

    public void accept(AgentTaskInputDTO input) {
        OnlineIdentityUtils.requireJwt(verifier.verify(input.taskCapability()), input.onlineIdentity());
        if (!properties.isOnlineCapabilityEnabled() && input.onlineIdentity() != null) { throw denied(); }
        // 即使 DataPart 删除线上字段，也必须由 Task 持久化身份拒绝普通证明冒充。
        var result = tasks.executionIdentity(input.workbenchTaskId().toString(), bearer(input));
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw denied(); }
        if (!Set.of("DISPATCHED", "RUNNING", "WAITING_INPUT", "WAITING_AUTH").contains(result.data())
                && executions.selectCount(new LambdaQueryWrapper<AgentExecutionEntity>().eq(AgentExecutionEntity::getWorkbenchTaskId, input.workbenchTaskId())) == 0) { throw denied(); }
        if (input.onlineIdentity() != null) { binding(input); }
    }

    public OnlineTaskBindingDTO binding(AgentTaskInputDTO input) {
        if (!properties.isOnlineCapabilityEnabled() || input.onlineIdentity() == null || !"LIVE".equals(input.executionMode())
                || input.sourceTaskId() != null || input.sourceExecutionId() != null || input.candidateConfigId() != null) { throw denied(); }
        OnlineIdentityUtils.requireJwt(verifier.verify(input.taskCapability()), input.onlineIdentity());
        var result = evaluation.executionPermit(input.workbenchTaskId().toString(), bearer(input));
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw denied(); }
        var permit = result.data(); var binding = permit.binding(); var identity = input.onlineIdentity();
        if (!identity.bindingHash().equals(permit.bindingHash()) || !identity.generation().equals(permit.generation())
                || !identity.permitHash().equals(permit.permitHash()) || !identity.experimentId().equals(binding.experimentId())
                || !identity.assignmentId().equals(binding.assignmentId()) || !input.workbenchTaskId().toString().equals(binding.taskId())
                || !input.agentId().toString().equals(binding.agentId()) || !input.spaceId().toString().equals(binding.spaceId())
                || !input.documentId().toString().equals(binding.documentId()) || !input.documentVersionSnapshot().toString().equals(binding.documentVersion())
                || !input.documentContentSha256().equals(binding.documentContentHash()) || !input.inputSnapshotHash().equals(binding.inputHash())
                || !Objects.equals(input.inputSnapshotSchemaVersion(), binding.inputSchemaVersion())
                || !input.tokenBudget().toString().equals(binding.tokenBudget()) || !"ORIGINAL".equals(binding.lineageType())) { throw denied(); }
        return binding;
    }
    public void begin(AgentTaskInputDTO input) {
        if (input.onlineIdentity() == null) { return; }
        binding(input);
        var result = evaluation.begin(input.workbenchTaskId().toString(), bearer(input));
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null || !result.data().started()) { throw denied(); }
    }
    private static String bearer(AgentTaskInputDTO input) { return JwtConstant.TOKEN_TYPE_BEARER + " " + input.taskCapability(); }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
