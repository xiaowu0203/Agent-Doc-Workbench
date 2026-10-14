package com.agentdoc.task.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewBatchDTO;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewDTO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangeItemVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalProposalPayloadVO;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.convertor.ChangeRequestConvertor;
import com.agentdoc.task.mapper.TaskOnlineOriginalProposalMapper;
import com.agentdoc.task.pojo.entity.TaskOnlineOriginalProposalEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static com.agentdoc.common.enums.OnlineReasonCode.ORIGINAL_EVIDENCE_UNAVAILABLE;
import static com.agentdoc.common.utils.OnlineOriginalChangeUtils.*;
import static com.agentdoc.common.constant.OnlineReviewConstant.MAX_TOTAL_LENGTH;

/** 读取全部原始提案或唯一草稿收尾版本；求值只使用冻结输入。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineOriginalChangesService {
    private final TaskOriginalEvidenceAccessService access;
    private final TaskOnlineOriginalProposalMapper originals;
    private final ChangeRequestService proposals;
    private final DocumentFeign documents;

    /** 先取得完整原始集合，再逐一复核精确载荷；不接受提案 ID 子集或审批状态筛选。 */
    public OnlineOriginalProposalPayloadVO payloads(Long taskId, Long spaceId, Long executionId) {
        var evidence = read(taskId, spaceId, executionId);
        var input = evidence.input();
        if (!AVAILABLE.equals(evidence.state()) || !"FORMAL".equals(input.documentType())) { throw unavailable(); }
        var rows = originals.selectList(new LambdaQueryWrapper<TaskOnlineOriginalProposalEntity>()
                .eq(TaskOnlineOriginalProposalEntity::getTaskId, taskId).orderByAsc(TaskOnlineOriginalProposalEntity::getId));
        if (!rows.stream().map(row -> row.getId().toString()).toList()
                .equals(evidence.items().stream().map(OnlineOriginalChangeItemVO::evidenceId).toList())) { throw unavailable(); }
        long length = 0;
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            if (!matches(input, row) || !Objects.equals(row.getPayloadHash(), evidence.items().get(index).payloadHash())) { throw unavailable(); }
            length += row.getOriginalChanges().length();
            if (length > MAX_TOTAL_LENGTH) { throw unavailable(); }
        }
        if (!input.equals(access.input(taskId, spaceId, executionId))
                || !proposals.agentProposalIds(taskId).equals(rows.stream().map(TaskOnlineOriginalProposalEntity::getId).toList())) { throw unavailable(); }
        return new OnlineOriginalProposalPayloadVO(evidence, rows.stream()
                .map(row -> new OnlineOriginalProposalPayloadVO.Item(row.getId().toString(), row.getOriginalChanges())).toList());
    }

    public OnlineOriginalChangesVO read(Long taskId, Long spaceId, Long executionId) {
        var input = access.input(taskId, spaceId, executionId);
        if (!input.completed()) { return empty(input, UNAVAILABLE); }
        if ("DRAFT".equals(input.documentType())) {
            var response = documents.getOnlineOriginalDraft(taskId, spaceId, executionId);
            if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null) { throw unavailable(); }
            if (!input.equals(response.data().input())) { return empty(input, INVALID); }
            return response.data();
        }
        var rows = originals.selectList(new LambdaQueryWrapper<TaskOnlineOriginalProposalEntity>()
                .eq(TaskOnlineOriginalProposalEntity::getTaskId, taskId).orderByAsc(TaskOnlineOriginalProposalEntity::getId));
        var sourceIds = proposals.agentProposalIds(taskId);
        if (sourceIds.isEmpty() || !sourceIds.equals(rows.stream().map(TaskOnlineOriginalProposalEntity::getId).toList())) {
            return empty(input, UNAVAILABLE);
        }
        var items = new ArrayList<OnlineOriginalChangeItemVO>();
        if (rows.stream().anyMatch(row -> !matches(input, row))) { return empty(input, INVALID); }
        var previews = documents.previewOnlineChanges(new OnlineProposalPreviewBatchDTO(Long.valueOf(input.documentId()),
                input.baseVersion(), input.inputHash(), rows.stream().map(row -> new OnlineProposalPreviewDTO(row.getId(),
                row.getBaseVersion(), ChangeRequestConvertor.parseChanges(row.getOriginalChanges()))).toList()));
        if (previews == null || previews.code() != ErrorCode.SUCCESS.getCode() || previews.data() == null) { throw unavailable(); }
        if (!rows.stream().map(TaskOnlineOriginalProposalEntity::getId).toList()
                .equals(previews.data().stream().map(value -> value.evidenceId()).toList())) { return empty(input, INVALID); }
        int index = 0;
        for (var row : rows) {
            var preview = previews.data().get(index++);
            items.add(signed(input, row.getId().toString(), PROPOSAL, row.getBaseVersion(), null, row.getPayloadHash(), preview.contentHash(),
                    preview.valid() && Objects.equals(row.getBaseVersion(), input.baseVersion()), row.getCapturedAt().toString()));
        }
        return new OnlineOriginalChangesVO(SCHEMA_VERSION, AVAILABLE, input, List.copyOf(items));
    }

    static boolean matches(OnlineOriginalInputVO input, TaskOnlineOriginalProposalEntity row) {
        try {
            return input.taskId().equals(row.getTaskId().toString()) && input.spaceId().equals(row.getSpaceId().toString())
                    && input.agentId().equals(row.getAgentId().toString()) && input.experimentId().equals(row.getExperimentId().toString())
                    && input.assignmentId().equals(row.getAssignmentId().toString()) && input.documentId().equals(row.getDocumentId().toString())
                    && input.bindingHash().equals(row.getBindingHash()) && input.inputHash().equals(row.getInputHash())
                    && row.getOriginalChanges() != null && StableSnapshotUtils.sha256Utf8(row.getOriginalChanges()).equals(row.getPayloadHash())
                    && TaskOnlineOriginalProposalService.identityHash(row).equals(row.getIdentityHash());
        } catch (RuntimeException invalid) { return false; }
    }
    private static OnlineOriginalChangesVO empty(OnlineOriginalInputVO input, String state) {
        return new OnlineOriginalChangesVO(SCHEMA_VERSION, state, input, List.of());
    }
    private static BusinessException unavailable() { return new BusinessException(ErrorCode.CONFLICT, ORIGINAL_EVIDENCE_UNAVAILABLE.name()); }
}
