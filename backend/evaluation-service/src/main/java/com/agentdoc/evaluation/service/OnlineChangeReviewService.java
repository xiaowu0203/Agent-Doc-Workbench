package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewBatchDTO;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewDTO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangeItemVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.OnlineFeedbackBlindness;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.vo.OnlineChangeReviewContextVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;
import static com.agentdoc.common.constant.OnlineReviewConstant.MAX_CONTENT_LENGTH;
import static com.agentdoc.common.constant.OnlineReviewConstant.MAX_TOTAL_LENGTH;

/** 读取完整集合；提案只读应用于冻结输入，草稿只读对应收尾版本。 */
@Service
@RequiredArgsConstructor
public class OnlineChangeReviewService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final SpaceAccessService access;
    private final TaskFeign tasks;
    private final DocumentFeign documents;
    private final OnlineTextReviewService textReviews;
    private final OnlineReviewExposureService exposures;

    public OnlineChangeReviewContextVO read(String experimentId, String assignmentId) {
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
        String body;
        List<OnlineChangeReviewContextVO.Item> originals;
        String setHash;
        if ("FORMAL".equals(input.documentType())) {
            var source = data(tasks.getOnlineOriginalProposalPayloads(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId()));
            validate(input, source.evidence());
            setHash = setHash(source.evidence());
            if (source.items() == null || !source.items().stream().map(item -> item.evidenceId()).toList()
                    .equals(source.evidence().items().stream().map(OnlineOriginalChangeItemVO::evidenceId).toList())) { throw invalid(); }
            var requests = new ArrayList<OnlineProposalPreviewDTO>(); long length = 0;
            for (int index = 0; index < source.items().size(); index++) {
                var payload = source.items().get(index); var proof = source.evidence().items().get(index);
                if (!OnlineOriginalChangeUtils.PROPOSAL.equals(proof.outputType()) || proof.outputVersion() != null
                        || payload.originalChanges() == null || !proof.payloadHash().equals(StableSnapshotUtils.sha256Utf8(payload.originalChanges()))) { throw invalid(); }
                length += payload.originalChanges().length(); if (length > MAX_TOTAL_LENGTH) { throw invalid(); }
                var changes = JsonUtils.parseStrict(payload.originalChanges(), ChangeItemDTO[].class);
                if (changes == null) { throw invalid(); }
                requests.add(new OnlineProposalPreviewDTO(OnlineProtocolUtils.id(proof.evidenceId()), proof.baseVersion(), Arrays.asList(changes)));
            }
            var previews = data(documents.previewOnlineChangesForReview(new OnlineProposalPreviewBatchDTO(OnlineProtocolUtils.id(input.documentId()),
                    input.baseVersion(), input.inputHash(), List.copyOf(requests))));
            body = previews.inputContent(); checkContent(body, input.inputHash());
            if (previews.items() == null || !previews.items().stream().map(item -> item.evidenceId().toString()).toList()
                    .equals(source.items().stream().map(item -> item.evidenceId()).toList())) { throw invalid(); }
            var items = new ArrayList<OnlineChangeReviewContextVO.Item>(); length += body.length();
            for (int index = 0; index < previews.items().size(); index++) {
                var preview = previews.items().get(index); var proof = source.evidence().items().get(index);
                if (preview.valid() != proof.structureValid() || !Objects.equals(preview.contentHash(), proof.contentHash())) { throw invalid(); }
                if (preview.valid()) { checkContent(preview.content(), proof.contentHash()); length += preview.content().length(); }
                else if (preview.content() != null || preview.contentHash() != null) { throw invalid(); }
                if (length > MAX_TOTAL_LENGTH) { throw invalid(); }
                items.add(view(proof, source.items().get(index).originalChanges(), preview.content()));
            }
            originals = List.copyOf(items);
        } else if ("DRAFT".equals(input.documentType())) {
            var source = data(tasks.getOnlineOriginalChanges(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId()));
            validate(input, source);
            setHash = setHash(source);
            if (source.items().size() != 1) { throw invalid(); }
            var proof = source.items().getFirst();
            if (!OnlineOriginalChangeUtils.DRAFT.equals(proof.outputType()) || !proof.structureValid()
                    || !Objects.equals(proof.baseVersion(), input.baseVersion()) || !Objects.equals(proof.outputVersion(), Math.addExact(input.baseVersion(), 1L))
                    || !Objects.equals(proof.payloadHash(), proof.contentHash())) { throw invalid(); }
            body = textReviews.readInputContent(input);
            var output = data(documents.getVersionExecutionContext(OnlineProtocolUtils.id(input.documentId()), proof.outputVersion(), proof.contentHash()));
            if (!Objects.equals(output.documentId(), OnlineProtocolUtils.id(input.documentId())) || !Objects.equals(output.version(), proof.outputVersion())
                    || !Objects.equals(output.contentSha256(), proof.contentHash()) || output.contentLength() == null
                    || output.contentLength() < 0 || output.contentLength() > MAX_CONTENT_LENGTH) { throw invalid(); }
            var fragment = data(documents.readVersionFragment(output.documentId(), output.version(), proof.contentHash(), 0, output.contentLength().intValue()));
            if (!Objects.equals(fragment.documentId(), output.documentId()) || fragment.start() != 0 || fragment.length() != output.contentLength()
                    || fragment.totalLength() != output.contentLength() || fragment.content() == null || fragment.content().length() != output.contentLength()) { throw invalid(); }
            checkContent(fragment.content(), proof.contentHash()); originals = List.of(view(proof, null, fragment.content()));
        } else { throw invalid(); }
        if (!input.equals(data(tasks.getOnlineOriginalInput(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId())))
                || !OnlineQualityFeedbackPersistenceService.matches(assignments.selectById(assignment.getId()), input)) { throw invalid(); }
        var exposure = exposures.known(experiment.getId(), actor);
        if (exposure != null && !Objects.equals(exposure.getSpaceId(), assignment.getSpaceId())) { throw invalid(); }
        return new OnlineChangeReviewContextVO(input.assignmentId(), input.taskId(), input.executionId(), input.documentId(), input.baseVersion(),
                input.inputHash(), body, input.assignmentId(), setHash, originals,
                exposure == null ? OnlineFeedbackBlindness.UNKNOWN.name() : OnlineFeedbackBlindness.NON_BLINDED.name());
    }

    private static void validate(OnlineOriginalInputVO input, OnlineOriginalChangesVO source) {
        if (source == null || source.schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION || !input.equals(source.input())
                || !OnlineOriginalChangeUtils.AVAILABLE.equals(source.state()) || source.items() == null || source.items().isEmpty()
                || source.items().stream().anyMatch(item -> !OnlineOriginalChangeUtils.valid(input, item))
                || source.items().stream().map(OnlineOriginalChangeItemVO::evidenceId).distinct().count() != source.items().size()) { throw invalid(); }
    }
    private static void checkContent(String content, String hash) {
        if (content == null || content.length() > MAX_CONTENT_LENGTH || !StableSnapshotUtils.sha256Utf8(content).equals(hash)) { throw invalid(); }
    }
    private static String setHash(OnlineOriginalChangesVO source) {
        try { return OnlineOriginalChangeUtils.setIdentityHash(source); }
        catch (IllegalArgumentException invalid) { throw invalid(); }
    }
    private static OnlineChangeReviewContextVO.Item view(OnlineOriginalChangeItemVO item, String changes, String content) {
        return new OnlineChangeReviewContextVO.Item(item.evidenceId(), item.outputType(), item.payloadHash(), item.baseVersion(), item.outputVersion(),
                item.structureValid(), changes, content);
    }
    private static <T> T data(Result<T> response) {
        if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null) { throw invalid(); }
        return response.data();
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, "原始变更评审证据不完整或不可访问"); }
}
