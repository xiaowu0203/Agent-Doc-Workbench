package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.vo.OnlineBlindReviewContextVO;
import com.agentdoc.evaluation.pojo.vo.OnlineReviewAssignmentVO;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 来源求证全部在事务外；资格短事务只能消费完整隐藏上下文和冻结用户目标。 */
@Service
@RequiredArgsConstructor
public class OnlineBlindReviewService {
    private final OnlineTextReviewService texts;
    private final OnlineChangeReviewService changes;
    private final OnlineAssignmentMapper assignments;
    private final TaskFeign tasks;
    private final OnlineBlindReviewReceiptService receipts;
    private final OnlineExperimentMapper experiments;
    private final SpaceAccessService access;
    /** 仅在已完成read的当前授权链路内取得Space，不生成普通元数据或曝光记录。 */
    String spaceId(String experimentId, String assignmentId) {
        var row = assignments.selectById(OnlineProtocolUtils.id(assignmentId));
        if (row == null || !Objects.equals(row.getExperimentId().toString(), experimentId)) { throw invalid(); }
        return row.getSpaceId().toString();
    }
    public PageVO<OnlineReviewAssignmentVO> assignments(String id, PageParam page) {
        AuthUtils.getUserIdOrException(); page.validate(); var experiment = experiments.selectById(OnlineProtocolUtils.id(id));
        if (experiment == null) { throw invalid(); }
        access.requirePermission(experiment.getSpaceId(), SPACE_READ); access.requirePermission(experiment.getSpaceId(), EVALUATION_RUN);
        access.requirePermission(experiment.getSpaceId(), TASK_READ); access.requirePermission(experiment.getSpaceId(), DOCUMENT_READ);
        var rows = assignments.selectPage(new Page<>(page.getPageNum(), page.getPageSize()), new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experiment.getId()).eq(OnlineAssignmentEntity::getSpaceId, experiment.getSpaceId())
                .eq(OnlineAssignmentEntity::getBindingSchemaVersion, 2).orderByAsc(OnlineAssignmentEntity::getAcceptedSequence));
        return PageVO.of(rows.getRecords().stream().map(row -> new OnlineReviewAssignmentVO(row.getId().toString(), row.getDocumentId().toString())).toList(), rows.getTotal(), page);
    }
    public OnlineBlindReviewContextVO read(String experimentId, String assignmentId, String target) {
        Long actor = AuthUtils.getUserIdOrException(); String type, evidenceId, evidenceHash; Object context;
        if ("TEXT".equals(target)) {
            var original = texts.read(experimentId, assignmentId); context = original; type = "ORIGINAL_TEXT";
            evidenceId = original.originalEvidenceId(); evidenceHash = original.originalEvidenceHash();
        } else if ("CHANGE_SET".equals(target)) {
            var original = changes.read(experimentId, assignmentId); context = original; type = "ORIGINAL_CHANGE_SET";
            evidenceId = original.originalSetId(); evidenceHash = original.originalSetHash();
        } else { throw new BusinessException(ErrorCode.BAD_REQUEST, "原始评审目标无效"); }
        var assignment = assignments.selectById(OnlineProtocolUtils.id(assignmentId));
        if (assignment == null || !Objects.equals(assignment.getExperimentId().toString(), experimentId)) { throw invalid(); }
        var response = tasks.getOnlineReviewGoal(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId());
        if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null || response.data().instruction() == null
                || !assignment.getTaskId().toString().equals(response.data().taskId()) || !Objects.equals(assignment.getInputSnapshotHash(), response.data().inputSnapshotHash())) { throw invalid(); }
        String knowledge = receipts.capture(assignment, actor, response.data(), type, evidenceId, evidenceHash);
        var originalContext = (ObjectNode) JsonUtils.parse(JsonUtils.toJson(context), JsonNode.class);
        originalContext.put("knowledgeStatus", knowledge);
        return new OnlineBlindReviewContextVO(assignmentId, knowledge, "BLINDED".equals(knowledge), response.data(), originalContext);
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, "冻结评审目标不可验证"); }
}
