package com.agentdoc.task.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TaskOnlineOriginalProposalMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TaskOnlineOriginalProposalEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import static com.agentdoc.common.enums.OnlineReasonCode.ORIGINAL_EVIDENCE_INVALID;

/** 原始提案与业务提交共用事务，任务行锁阻止终态后补交。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineOriginalProposalService {
    private final TaskMapper tasks;
    private final TaskOnlineOriginalProposalMapper originals;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void capture(TaskEntity origin, ChangeRequestEntity proposal) {
        if (origin.getOnlineAssignmentId() == null) { return; }
        var task = tasks.selectOne(new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getId, origin.getId()).last("FOR UPDATE"));
        if (task == null || !TaskExecutionMode.LIVE.name().equals(task.getExecutionMode())
                || TaskStatus.fromCode(task.getStatus()) == null || !TaskStatus.fromCode(task.getStatus()).allowsCapabilityAccess()
                || !Integer.valueOf(2).equals(task.getOnlineBindingSchemaVersion())
                || !Objects.equals(task.getOnlineBindingHash(), origin.getOnlineBindingHash())
                || !Objects.equals(task.getOnlineAssignmentId(), origin.getOnlineAssignmentId())
                || !Objects.equals(task.getOnlineExperimentId(), origin.getOnlineExperimentId())
                || !Objects.equals(task.getSpaceId(), origin.getSpaceId()) || !Objects.equals(task.getAgentId(), origin.getAgentId())
                || !Objects.equals(task.getDocumentContentSha256(), origin.getDocumentContentSha256())
                || !Objects.equals(task.getId(), proposal.getSourceTaskId())
                || !Objects.equals(task.getSpaceId(), proposal.getSpaceId()) || !Objects.equals(task.getAgentId(), proposal.getProposedBy())
                || !Integer.valueOf(ActorType.AGENT.getCode()).equals(proposal.getProposedActorType())
                || !Objects.equals(task.getDocumentId(), proposal.getDocumentId()) || proposal.getId() == null || proposal.getBaseVersion() == null) {
            throw invalid();
        }
        var row = new TaskOnlineOriginalProposalEntity(); row.setId(proposal.getId()); row.setTaskId(task.getId());
        row.setSpaceId(task.getSpaceId()); row.setAgentId(task.getAgentId()); row.setExperimentId(task.getOnlineExperimentId());
        row.setAssignmentId(task.getOnlineAssignmentId()); row.setDocumentId(task.getDocumentId()); row.setBaseVersion(proposal.getBaseVersion());
        row.setInputHash(task.getDocumentContentSha256()); row.setBindingHash(task.getOnlineBindingHash()); row.setOriginalChanges(proposal.getChanges());
        row.setPayloadHash(StableSnapshotUtils.sha256Utf8(row.getOriginalChanges())); row.setCapturedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        row.setIdentityHash(identityHash(row)); originals.insert(row);
    }

    public static String identityHash(TaskOnlineOriginalProposalEntity row) {
        return OnlineProtocolUtils.hash("online.original-proposal", Map.ofEntries(Map.entry("proposalId", row.getId().toString()),
                Map.entry("taskId", row.getTaskId().toString()), Map.entry("spaceId", row.getSpaceId().toString()),
                Map.entry("agentId", row.getAgentId().toString()), Map.entry("experimentId", row.getExperimentId().toString()),
                Map.entry("assignmentId", row.getAssignmentId().toString()), Map.entry("documentId", row.getDocumentId().toString()),
                Map.entry("baseVersion", row.getBaseVersion()), Map.entry("inputHash", row.getInputHash()),
                Map.entry("bindingHash", row.getBindingHash()), Map.entry("payloadHash", row.getPayloadHash()),
                Map.entry("capturedAt", row.getCapturedAt().toString())));
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, ORIGINAL_EVIDENCE_INVALID.name()); }
}
