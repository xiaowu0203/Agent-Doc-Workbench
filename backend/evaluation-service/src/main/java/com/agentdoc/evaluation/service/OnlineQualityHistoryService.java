package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.param.OnlineQualityHistorySearchParam;
import com.agentdoc.evaluation.pojo.vo.OnlineQualityFeedbackVO;
import com.agentdoc.evaluation.pojo.vo.OnlineQualityHistoryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

/** 来源读沿用隐藏评审权限；只取一个assignment的完整链，再返回分页，不能用分页猜测链尾。 */
@Service
@RequiredArgsConstructor
public class OnlineQualityHistoryService {
    private final OnlineBlindReviewService reviews;
    private final EvaluationFeedbackMapper feedback;

    public OnlineQualityHistoryVO search(String experimentId, String assignmentId, OnlineQualityHistorySearchParam param) {
        param.validate();
        Long actor = AuthUtils.getUserIdOrException();
        var context = reviews.read(experimentId, assignmentId, param.getTarget());
        String spaceId = reviews.spaceId(experimentId, assignmentId);
        var original = context.originalContext();
        String type = "TEXT".equals(param.getTarget()) ? "ORIGINAL_TEXT" : "ORIGINAL_CHANGE_SET";
        String evidenceId = original.path("TEXT".equals(param.getTarget()) ? "originalEvidenceId" : "originalSetId").asText();
        String evidenceHash = original.path("TEXT".equals(param.getTarget()) ? "originalEvidenceHash" : "originalSetHash").asText();
        var rows = feedback.selectList(new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                .eq(EvaluationFeedbackEntity::getOnlineAssignmentId, OnlineProtocolUtils.id(assignmentId))
                .eq(EvaluationFeedbackEntity::getSourceType, "MANUAL").isNotNull(EvaluationFeedbackEntity::getRubricScore));
        for (var row : rows) {
            if (!OnlineQualityFeedbackFacts.valid(row) || !Objects.equals(row.getOnlineAssignmentId().toString(), assignmentId)
                    || !Objects.equals(row.getSpaceId().toString(), spaceId)
                    || !Objects.equals(row.getTaskId().toString(), original.path("taskId").asText())
                    || !Objects.equals(row.getExecutionId().toString(), original.path("executionId").asText())
                    || !Objects.equals(row.getOriginalEvidenceId().toString(), evidenceId) || !Objects.equals(row.getOriginalEvidenceHash(), evidenceHash)) { throw invalid(); }
            var facts = OnlineProtocolUtils.object(row.getFactsJson());
            if (!Objects.equals(facts.path("originalEvidenceType").asText(), type)
                    || !Objects.equals(facts.path("input").path("experimentId").asText(), experimentId)
                    || !Objects.equals(facts.path("input").path("inputHash").asText(), original.path("inputHash").asText())
                    || !Objects.equals(facts.path("input").path("documentId").asText(), original.path("documentId").asText())
                    || !Objects.equals(facts.path("input").path("baseVersion").asText(), original.path("baseVersion").asText())) { throw invalid(); }
        }
        var ordered = OnlineQualityFeedbackFacts.orderedChain(rows);
        if (ordered == null) { throw invalid(); }
        var tail = ordered.isEmpty() ? null : ordered.getLast();
        var own = ordered.reversed().stream().filter(row -> actor.equals(row.getCreatedBy())).findFirst().orElse(null);
        int from = (int) Math.min((long) (param.getPageNum() - 1) * param.getPageSize(), ordered.size());
        int to = Math.min(from + param.getPageSize(), ordered.size());
        return new OnlineQualityHistoryVO(OnlineProtocolUtils.SCHEMA_VERSION, spaceId, experimentId, assignmentId, original.path("taskId").asText(),
                original.path("executionId").asText(), type, evidenceId, evidenceHash,
                tail == null ? null : tail.getId().toString(), tail == null ? null : tail.getBlindness(),
                own == null ? null : item(own, actor), PageVO.of(ordered.subList(from, to).stream().map(row -> item(row, actor)).toList(), ordered.size(), param));
    }

    private static OnlineQualityHistoryVO.Item item(EvaluationFeedbackEntity row, Long actor) {
        var quality = OnlineQualityFeedbackVO.from(row);
        boolean own = actor.equals(row.getCreatedBy());
        return new OnlineQualityHistoryVO.Item(row.getId().toString(), row.getSourceHash(), quality.supersedesFeedbackId(),
                quality.rubricScore(), row.getLabel(), quality.blindness(), quality.declaredBlindness(), own,
                own ? row.getComment() : null, own ? quality.reasonCodes() : List.of(), quality.seriousSafetyEvent(),
                own ? quality.safetyReason() : null, row.getCreatedAt());
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, "原始质量反馈链或来源不可核验，请检查相同原始产物目标"); }
}
