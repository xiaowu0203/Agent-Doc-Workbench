package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.OnlineApprovalDifferenceUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeRequestFeedbackProjectionServiceTest {
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private DocumentFeign documentFeign;
    @Mock private TaskOnlineApprovalSourceService onlineSources;

    private ChangeRequestFeedbackProjectionService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestFeedbackProjectionService(changeRequestMapper, taskMapper, documentFeign, onlineSources);
    }

    @Test
    void projectsFinalApprovalAsStableRedactedSnapshot() {
        ChangeRequestEntity request = new ChangeRequestEntity();
        request.setId(11L); request.setSpaceId(9L); request.setSourceTaskId(21L);
        request.setStatus(ChangeRequestStatus.MERGED.getCode()); request.setResolutionType("PARTIAL");
        request.setReviewComment("private review"); request.setReviewedBy(31L);
        request.setReviewedAt(LocalDateTime.of(2026, 9, 21, 10, 0)); request.setRevisionNo(2);
        TaskEntity task = new TaskEntity(); task.setId(21L); task.setAgentExecutionId(41L);
        when(changeRequestMapper.selectById(11L)).thenReturn(request);
        when(taskMapper.selectById(21L)).thenReturn(task);
        when(documentFeign.checkSpacePermission(eq(9L), anyString())).thenReturn(Result.ok());

        var first = service.get(11L);
        var second = service.get(11L);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo("MERGED");
        assertThat(first.executionId()).isEqualTo(41L);
        assertThat(first.reviewCommentHash()).hasSize(64).doesNotContain("private review");
        assertThat(first.sourceHash()).hasSize(64);
        assertThat(first.onlineApprovalDifference()).isNull();
        assertThat(first.sourceHash()).isEqualTo(StableSnapshotUtils.snapshotHash(1, Map.ofEntries(
                Map.entry("changeRequestId", 11L), Map.entry("status", "MERGED"), Map.entry("resolutionType", "PARTIAL"),
                Map.entry("reviewCommentHash", StableSnapshotUtils.sha256Utf8("private review")), Map.entry("reviewedBy", 31L),
                Map.entry("reviewedAt", "2026-09-21T10:00"), Map.entry("revisionNo", 2))));
        verifyNoInteractions(onlineSources);
        task.setOnlineAssignmentId(51L); task.setOnlineBindingSchemaVersion(1);
        assertThat(service.get(11L)).isEqualTo(first);
        verifyNoInteractions(onlineSources);
    }

    private OnlineApprovalSourceVO online(ChangeRequestStatus status) {
        var request = new ChangeRequestEntity(); request.setId(11L); request.setSpaceId(9L); request.setSourceTaskId(21L); request.setStatus(status.getCode());
        boolean merged = status == ChangeRequestStatus.MERGED;
        if (merged) { request.setMergedVersion(4L); request.setMergedBy(501L); request.setMergedAt(LocalDateTime.parse("2026-10-10T20:00:00")); }
        var task = new TaskEntity(); task.setId(21L); task.setAgentExecutionId(41L); task.setOnlineAssignmentId(51L); task.setOnlineBindingSchemaVersion(2);
        when(changeRequestMapper.selectById(11L)).thenReturn(request); when(taskMapper.selectById(21L)).thenReturn(task);
        when(documentFeign.checkSpacePermission(eq(9L), anyString())).thenReturn(Result.ok());
        return new OnlineApprovalSourceVO(new OnlineOriginalInputVO("21", "41", "9", "20", "10", "51", "a".repeat(64), "61", "FORMAL", 3L, "b".repeat(64), true),
                "11", status.name(), "c".repeat(64), "d".repeat(64), 3L, null, merged ? 4L : null,
                merged ? "501" : null, merged ? "2026-10-10T20:00" : null, merged ? null : "NOT_MERGED");
    }
    @Test void onlineApprovalIsIndependentMissingProofAndUsesANewHashWithoutCallingDocumentForUnmerged() {
        var source = online(ChangeRequestStatus.APPROVED); when(onlineSources.get(11L)).thenReturn(source);
        var result = service.get(11L);
        assertThat(result.onlineApprovalDifference().reason()).isEqualTo("NOT_MERGED");
        assertThat(result.onlineApprovalDifference().humanModificationRatio()).isNull();
        assertThat(result.sourceHash()).isEqualTo(OnlineApprovalDifferenceUtils.feedbackHash(result));
        verify(documentFeign, never()).getOnlineApprovalDifference(any());
    }
    @Test void aStateChangeDuringProjectionIsRejectedRatherThanMixingTwoSnapshots() {
        var source = online(ChangeRequestStatus.APPROVED);
        when(onlineSources.get(11L)).thenReturn(new OnlineApprovalSourceVO(source.input(), source.changeRequestId(), "MERGED",
                source.originalEvidenceHash(), source.originalIdentityHash(), source.baseVersion(), null, 4L, "501", "2026-10-10T20:00:00", null));
        assertThatThrownBy(() -> service.get(11L)).isInstanceOf(BusinessException.class).hasMessageContaining("已变化");
    }
    @Test void mergedDifferenceMustMatchActualTaskSourceEvenWhenEnvelopeHashIsInternallyValid() {
        var source = online(ChangeRequestStatus.MERGED); when(onlineSources.get(11L)).thenReturn(source);
        var different = new OnlineApprovalSourceVO(source.input(), "99", "MERGED", source.originalEvidenceHash(), source.originalIdentityHash(),
                source.baseVersion(), null, source.mergedVersion(), source.mergedBy(), source.mergedAt(), null);
        when(documentFeign.getOnlineApprovalDifference(11L)).thenReturn(Result.ok(OnlineApprovalDifferenceUtils.signed(different, null,
                "e".repeat(64), "f".repeat(64), 1, 1, 1, BigDecimal.ONE)));
        assertThatThrownBy(() -> service.get(11L)).isInstanceOf(BusinessException.class).hasMessageContaining("身份不一致");
    }

    @Test
    void rejectsNonFinalChangeRequest() {
        ChangeRequestEntity request = new ChangeRequestEntity();
        request.setId(11L); request.setStatus(ChangeRequestStatus.PENDING.getCode());
        when(changeRequestMapper.selectById(11L)).thenReturn(request);

        assertThatThrownBy(() -> service.get(11L)).isInstanceOf(BusinessException.class);
    }
}
