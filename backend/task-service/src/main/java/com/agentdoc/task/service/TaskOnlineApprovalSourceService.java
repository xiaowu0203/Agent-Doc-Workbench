package com.agentdoc.task.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.convertor.ChangeRequestConvertor;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TaskOnlineOriginalProposalMapper;
import com.agentdoc.task.pojo.entity.TaskOnlineOriginalProposalEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.List;
import static com.agentdoc.common.utils.OnlineApprovalDifferenceUtils.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

/** 原始审批来源由 Task 的真实记录产生，不接受调用者上传的提案作为证据。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineApprovalSourceService {
    private final TaskMapper tasks;
    private final TaskOnlineOriginalProposalMapper originals;
    private final ChangeRequestService proposals;
    private final TaskOriginalEvidenceAccessService access;
    private final DocumentFeign documents;

    public OnlineApprovalSourceVO get(Long id) {
        AuthUtils.getUserIdOrException();
        var request = proposals.requireRequest(id);
        var task = request.getSourceTaskId() == null ? null : tasks.selectById(request.getSourceTaskId());
        if (task == null || task.getOnlineAssignmentId() == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "线上审批来源不存在"); }
        if (!Objects.equals(request.getSpaceId(), task.getSpaceId()) || !Objects.equals(request.getDocumentId(), task.getDocumentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "审批与来源任务的文档归属不一致");
        }
        var input = access.input(task.getId(), task.getSpaceId(), task.getAgentExecutionId());
        for (String permission : List.of(EVALUATION_RUN, CHANGE_REQUEST_READ)) {
            var result = documents.checkSpacePermission(task.getSpaceId(), permission);
            if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
                throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(), "原始审批来源访问被拒绝");
            }
        }
        if (!"FORMAL".equals(input.documentType())) { throw new BusinessException(ErrorCode.CONFLICT, "线上审批来源必须是正式文档提案"); }
        var rows = originals.selectList(new LambdaQueryWrapper<TaskOnlineOriginalProposalEntity>()
                .eq(TaskOnlineOriginalProposalEntity::getTaskId, task.getId()).orderByAsc(TaskOnlineOriginalProposalEntity::getId));
        var sourceIds = proposals.agentProposalIds(task.getId());
        var original = rows.stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null);
        String reason = null;
        if (!input.completed()) { reason = TASK_NOT_COMPLETED; }
        else if (original == null || !sourceIds.equals(rows.stream().map(TaskOnlineOriginalProposalEntity::getId).toList())) { reason = ORIGINAL_UNAVAILABLE; }
        else if (rows.stream().anyMatch(row -> !TaskOnlineOriginalChangesService.matches(input, row))
                || !Objects.equals(request.getSpaceId(), task.getSpaceId()) || !Objects.equals(request.getDocumentId(), task.getDocumentId())
                || !Objects.equals(request.getProposedBy(), task.getAgentId()) || !Integer.valueOf(ActorType.AGENT.getCode()).equals(request.getProposedActorType())) {
            reason = ORIGINAL_INVALID;
        } else if (rows.size() != 1) { reason = MULTIPLE_SOURCES; }
        else if (!Objects.equals(original.getBaseVersion(), input.baseVersion())) { reason = BASE_VERSION_MISMATCH; }
        else if (!Integer.valueOf(ChangeRequestStatus.MERGED.getCode()).equals(request.getStatus())) { reason = NOT_MERGED; }
        else if (request.getMergedVersion() == null || request.getMergedBy() == null || request.getMergedAt() == null
                || request.getMergedVersion() <= input.baseVersion()) { reason = MERGED_INVALID; }
        boolean originalValid = original != null && TaskOnlineOriginalChangesService.matches(input, original);
        var status = ChangeRequestStatus.fromCode(request.getStatus());
        return new OnlineApprovalSourceVO(input, id.toString(), status == null ? null : status.name(), originalValid ? original.getPayloadHash() : null,
                originalValid ? original.getIdentityHash() : null, originalValid ? original.getBaseVersion() : null,
                reason == null ? ChangeRequestConvertor.parseChanges(original.getOriginalChanges()) : null,
                request.getMergedVersion(), request.getMergedBy() == null ? null : request.getMergedBy().toString(),
                request.getMergedAt() == null ? null : request.getMergedAt().toString(), reason);
    }
}
