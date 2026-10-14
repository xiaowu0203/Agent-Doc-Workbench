package com.agentdoc.agent.a2a.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.service.AgentExecutionQueryService;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.feign.dto.TaskRecoveryIdentityDTO;
import com.agentdoc.common.feign.vo.TaskRecoveryRemoteVO;
import com.agentdoc.common.security.TaskRecoveryVerifier;
import com.agentdoc.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Task;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;

/** 只能读取/取消同一既有 A2A Task，不能创建执行或恢复通用 Agent 身份。 */
@Service
@RequiredArgsConstructor
public class A2aTaskRecoveryService {
    private final TaskRecoveryVerifier verifier;
    private final AgentExecutionMapper executionMapper;
    private final TaskStore taskStore;
    private final RequestHandler requestHandler;
    private final AgentExecutionQueryService queryService;

    public TaskRecoveryRemoteVO<Task> recover(String a2aId, String token, boolean cancel) throws A2AError {
        Jwt jwt = verifier.verify(token, cancel ? TaskRecoveryConstant.CANCEL : TaskRecoveryConstant.QUERY, a2aId);
        List<AgentExecutionEntity> executions = executionMapper.selectList(new LambdaQueryWrapper<AgentExecutionEntity>()
                .eq(AgentExecutionEntity::getA2aTaskId, a2aId));
        if (executions.size() != 1) { throw mismatch(); }
        AgentExecutionEntity execution = executions.getFirst();
        Task remote = taskStore.get(a2aId);
        if (remote == null || !a2aId.equals(remote.id()) || remote.status() == null || remote.status().state() == null
                || !Objects.equals(remote.contextId(), execution.getA2aContextId())) { throw mismatch(); }
        // 配置快照不包含业务输入，使用既有加密 A2A 历史中的最初 DataPart 核对输入；缺失时 fail-closed。
        AgentTaskInputDTO input = originalInput(remote);
        if (!Objects.equals(execution.getWorkbenchTaskId(), input.workbenchTaskId())
                || !Objects.equals(execution.getAgentId(), input.agentId())
                || !Objects.equals(execution.getSpaceId(), input.spaceId())) { throw mismatch(); }
        boolean online = Stream.of(execution.getOnlineExperimentId(), execution.getOnlineAssignmentId(), execution.getOnlineBindingSchemaVersion(),
                execution.getOnlineBindingHash(), execution.getOnlineSlotGeneration(), execution.getOnlineSlotPermitHash()).anyMatch(Objects::nonNull);
        var dispatch = online ? new OnlineDispatchIdentityDTO(String.valueOf(execution.getOnlineExperimentId()),
                String.valueOf(execution.getOnlineAssignmentId()), execution.getOnlineBindingSchemaVersion(), execution.getOnlineBindingHash(),
                execution.getOnlineSlotGeneration(), execution.getOnlineSlotPermitHash()) : null;
        if (!Objects.equals(dispatch, input.onlineIdentity())) { throw mismatch(); }
        verifier.requireIdentity(jwt, new TaskRecoveryIdentityDTO(input.workbenchTaskId(), input.agentId(), input.spaceId(),
                input.documentId(), input.executionMode(), input.documentVersionSnapshot(), input.documentContentSha256(),
                input.inputSnapshotSchemaVersion(), input.inputSnapshotHash(), input.derivationRequestHash(), input.onlineIdentity()));
        requireSourceJti(jwt, input.taskCapability());
        if (cancel && !remote.status().state().isFinal()) {
            remote = requestHandler.onCancelTask(new CancelTaskParams(a2aId),
                    new ServerCallContext(null, Map.of(), Set.of(), null));
            if (remote == null || !a2aId.equals(remote.id()) || remote.status() == null || remote.status().state() == null
                    || !Objects.equals(remote.contextId(), execution.getA2aContextId())) { throw mismatch(); }
        }
        var usage = queryService.getTokenUsageByWorkbenchTask(execution.getWorkbenchTaskId());
        if (usage != null && !Objects.equals(execution.getId(), usage.executionId())) { throw mismatch(); }
        // 输入历史仅用于本地核验，不把包含原能力凭证的 DataPart 返回给调用方。
        return new TaskRecoveryRemoteVO<>(Task.builder(remote).history(List.of()).build(), usage);
    }

    private void requireSourceJti(Jwt jwt, String original) {
        try {
            // 原输入来自既有加密存储；这里只比对当初已验证过的证明身份，不用它建立认证上下文。
            if (original == null || original.length() > TaskRecoveryConstant.MAX_PROOF_LENGTH) { throw mismatch(); }
            String[] parts = original.split("\\.");
            if (parts.length != 3) { throw mismatch(); }
            JsonNode payload = JsonUtils.parse(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), JsonNode.class);
            if (payload == null || !Objects.equals(payload.path("jti").asText(), jwt.getClaimAsString(TaskRecoveryConstant.SOURCE_JTI))) { throw mismatch(); }
        } catch (RuntimeException exception) { throw mismatch(); }
    }

    private AgentTaskInputDTO originalInput(Task remote) {
        if (remote.history() == null) { throw mismatch(); }
        List<AgentTaskInputDTO> inputs = remote.history().stream()
                .flatMap(message -> message.parts().stream()).filter(DataPart.class::isInstance).map(DataPart.class::cast)
                .map(part -> JsonUtils.parse(JsonUtils.toJson(part.data()), AgentTaskInputDTO.class))
                .filter(input -> input != null && input.workbenchTaskId() != null).toList();
        if (inputs.size() != 1) { throw mismatch(); }
        return inputs.getFirst();
    }

    private BusinessException mismatch() {
        return new BusinessException(ErrorCode.CONFLICT, "RECOVERY_IDENTITY_MISMATCH");
    }
}
