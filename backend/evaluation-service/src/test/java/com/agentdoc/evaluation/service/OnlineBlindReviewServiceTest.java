package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineTextReviewContextVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineBlindReviewServiceTest {
    private final OnlineTextReviewService texts = mock(OnlineTextReviewService.class);
    private final OnlineChangeReviewService changes = mock(OnlineChangeReviewService.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final OnlineBlindReviewReceiptService receipts = mock(OnlineBlindReviewReceiptService.class);
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final OnlineBlindReviewService service = new OnlineBlindReviewService(texts, changes, assignments, tasks, receipts, experiments, access, documents);

    @Test void candidateNamesUseOnlyOneDocumentReferenceBatchAndNeverReadOrdinaryTaskOrCreateReceipt() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L);
            var experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(10L);
            when(experiments.selectById(11L)).thenReturn(experiment);
            var row = row(); row.setDocumentId(41L);
            when(assignments.selectPage(any(Page.class), any())).thenReturn(new Page<OnlineAssignmentEntity>(1, 10).setRecords(List.of(row)).setTotal(1));
            when(documents.getDocumentRefs(List.of(41L))).thenReturn(Result.ok(List.of(new DocumentRefVO(41L, 10L, "核对文档"))));
            var result = service.assignments("11", new PageParam());
            assertThat(result.records().getFirst().documentTitle()).isEqualTo("核对文档");
            assertThat(JsonUtils.parse(JsonUtils.toJson(result), JsonNode.class).findValues("variant")).isEmpty();
            verify(documents).getDocumentRefs(List.of(41L)); verifyNoInteractions(tasks, texts, changes, receipts);
            when(documents.getDocumentRefs(List.of(41L))).thenReturn(Result.ok(List.of(new DocumentRefVO(41L, 99L, "其他空间"))));
            assertThatThrownBy(() -> service.assignments("11", new PageParam())).isInstanceOf(BusinessException.class);
        }
    }
    private OnlineAssignmentEntity row() { var row = new OnlineAssignmentEntity(); row.setId(51L); row.setExperimentId(11L); row.setSpaceId(10L); row.setTaskId(61L); row.setExecutionId(71L); row.setInputSnapshotHash("b".repeat(64)); return row; }
    private void sources() {
        when(texts.read("11", "51")).thenReturn(new OnlineTextReviewContextVO("51", "61", "71", "41", 3L, "a".repeat(64), "完整冻结正文", "71", "c".repeat(64), "完整原始产物", "UNKNOWN"));
        when(assignments.selectById(51L)).thenReturn(row()); when(tasks.getOnlineReviewGoal(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineReviewGoalVO("61", "b".repeat(64), "完整用户目标")));
    }
    @Test void onlyCompleteHiddenInputAndFrozenUserGoalCanProduceQualificationAndNoMetadataLeaks() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); sources(); when(receipts.capture(any(), eq(502L), any(), eq("ORIGINAL_TEXT"), eq("71"), eq("c".repeat(64)))).thenReturn("BLINDED");
            var result = service.read("11", "51", "TEXT"); assertThat(result.blindedEligible()).isTrue(); assertThat(result.goal().instruction()).isEqualTo("完整用户目标");
            assertThat(result.originalContext().path("knowledgeStatus").asText()).isEqualTo("BLINDED");
            var node = JsonUtils.parse(JsonUtils.toJson(result), JsonNode.class);
            for (String forbidden : Set.of("variant", "prompt", "modelId", "agentId", "templateId", "cost", "usage", "manifestHash")) { assertThat(node.findValues(forbidden)).isEmpty(); }
        }
    }
    @Test void wrongFrozenGoalOrSourcePermissionFailureCannotCreateReceipt() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); sources(); when(tasks.getOnlineReviewGoal(61L, 10L, 71L)).thenReturn(Result.ok(new OnlineReviewGoalVO("61", "a".repeat(64), "被更改的目标")));
            assertThatThrownBy(() -> service.read("11", "51", "TEXT")).isInstanceOf(BusinessException.class); verifyNoInteractions(receipts);
            when(texts.read("11", "51")).thenThrow(new BusinessException(403, "撤权"));
            assertThatThrownBy(() -> service.read("11", "51", "TEXT")).isInstanceOf(BusinessException.class); verifyNoInteractions(receipts);
        }
    }
    @Test void clientCannotChooseKnowledgeStatusAndUnauthenticatedCallerFailsBeforeSources() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenThrow(new BusinessException(401, "缺少当前用户"));
            assertThatThrownBy(() -> service.read("11", "51", "TEXT")).isInstanceOf(BusinessException.class); verifyNoInteractions(texts, changes, tasks, receipts);
        }
    }
}
