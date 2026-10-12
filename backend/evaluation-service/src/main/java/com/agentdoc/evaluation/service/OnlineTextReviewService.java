package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.OnlineFeedbackBlindness;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.vo.OnlineTextReviewContextVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;
import static com.agentdoc.common.constant.OnlineReviewConstant.MAX_CONTENT_LENGTH;

/** 人类评审只看冻结输入与完整原文，不复用实验管理投影或审批后的正文。 */
@Service
@RequiredArgsConstructor
public class OnlineTextReviewService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final SpaceAccessService access;
    private final TaskFeign tasks;
    private final DocumentFeign documents;
    private final OnlineQualityFeedbackService quality;
    private final OnlineReviewExposureService exposures;

    public OnlineTextReviewContextVO read(String experimentId, String assignmentId) {
        Long actor = AuthUtils.getUserIdOrException();
        var experiment = experiments.selectById(OnlineProtocolUtils.id(experimentId));
        if (experiment == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "实验不存在"); }
        access.requirePermission(experiment.getSpaceId(), SPACE_READ); access.requirePermission(experiment.getSpaceId(), EVALUATION_RUN);
        var assignment = assignments.selectById(OnlineProtocolUtils.id(assignmentId));
        if (assignment == null || !Objects.equals(assignment.getExperimentId(), experiment.getId())
                || !Objects.equals(assignment.getSpaceId(), experiment.getSpaceId()) || assignment.getExecutionId() == null
                || !Integer.valueOf(OnlineProtocolUtils.SCHEMA_VERSION).equals(experiment.getManifestSchemaVersion())) { throw invalid(); }
        var input = data(tasks.getOnlineOriginalInput(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId()));
        if (!input.completed() || !OnlineQualityFeedbackPersistenceService.matches(assignment, input)) { throw invalid(); }
        var original = quality.readOriginalText(input);
        if (original.originalText().length() > MAX_CONTENT_LENGTH) { throw invalid(); }
        var content = readInputContent(input);
        // 在正文返回前重新核验当前 Task/文档归属，不能沿用开始读取时的权限。
        if (!Objects.equals(input, data(tasks.getOnlineOriginalInput(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId())))
                || !OnlineQualityFeedbackPersistenceService.matches(assignments.selectById(assignment.getId()), input)) { throw invalid(); }
        var exposure = exposures.known(experiment.getId(), actor);
        if (exposure != null && !Objects.equals(exposure.getSpaceId(), assignment.getSpaceId())) { throw invalid(); }
        return new OnlineTextReviewContextVO(input.assignmentId(), input.taskId(), input.executionId(), input.documentId(), input.baseVersion(),
                input.inputHash(), content, original.evidenceId(), original.contentHash(), original.originalText(),
                exposure == null ? OnlineFeedbackBlindness.UNKNOWN.name() : OnlineFeedbackBlindness.NON_BLINDED.name());
    }

    /** 文本与变更评审共用完整冻结输入核验，不回退到当前正文。 */
    String readInputContent(OnlineOriginalInputVO input) {
        var documentId = OnlineProtocolUtils.id(input.documentId());
        var version = data(documents.getVersionExecutionContext(documentId, input.baseVersion(), input.inputHash()));
        if (!Objects.equals(version.documentId(), documentId) || !Objects.equals(version.version(), input.baseVersion())
                || !Objects.equals(version.contentSha256(), input.inputHash()) || version.contentLength() == null
                || version.contentLength() < 0 || version.contentLength() > MAX_CONTENT_LENGTH) { throw invalid(); }
        var fragment = data(documents.readVersionFragment(documentId, input.baseVersion(), input.inputHash(), 0, version.contentLength().intValue()));
        if (!Objects.equals(fragment.documentId(), documentId) || fragment.content() == null || fragment.start() != 0
                || fragment.totalLength() != version.contentLength() || fragment.length() != version.contentLength()
                || fragment.content().length() != version.contentLength() || !StableSnapshotUtils.sha256Utf8(fragment.content()).equals(input.inputHash())) { throw invalid(); }
        return fragment.content();
    }
    private static <T> T data(Result<T> value) {
        if (value == null || value.code() != ErrorCode.SUCCESS.getCode() || value.data() == null) { throw invalid(); }
        return value.data();
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, "原始文本评审证据不完整或不可访问"); }
}
