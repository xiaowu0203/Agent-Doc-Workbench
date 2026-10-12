package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.OnlineApprovalDifferenceUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.feign.vo.OnlineApprovalDifferenceVO;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.entity.DocumentVersionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentOnlineApprovalDifferenceTest {
    private final DocumentMapper mapper = mock(DocumentMapper.class);
    private final DocumentVersionService versions = mock(DocumentVersionService.class);
    private final SpacePermissionService permissions = mock(SpacePermissionService.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final DocumentService documents = new DocumentService(mapper, mock(DocumentDirectoryService.class), versions, permissions, mock(AuthFeign.class));
    private final DocumentOnlineApprovalDifferenceService service = new DocumentOnlineApprovalDifferenceService(tasks, documents, permissions);
    private OnlineApprovalSourceVO source;
    private DocumentVersionEntity merged;
    private DocumentEntity document;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256").subject("501").claim("scope", "user").build()));
        var input = new OnlineOriginalInputVO("61", "71", "10", "20", "11", "51", "a".repeat(64), "41", "FORMAL", 3L, StableSnapshotUtils.sha256Utf8("基线"), true);
        source = new OnlineApprovalSourceVO(input, "91", "MERGED", "b".repeat(64), "c".repeat(64), 3L,
                List.of(new ChangeItemDTO(ChangeOp.REPLACE, null, "原始😀正文")), 4L, "501", "2026-10-10T20:00:00", null);
        when(tasks.getOnlineApprovalSource(91L)).thenReturn(Result.ok(source));
        document = new DocumentEntity(); document.setId(41L); document.setSpaceId(10L); document.setContent("后续人工正文"); document.setVersion(12L);
        when(mapper.selectById(41L)).thenReturn(document);
        var base = new DocumentVersionEntity(); base.setContent("基线"); base.setContentSha256(input.inputHash()); when(versions.requireVersion(41L, 3L)).thenReturn(base);
        merged = DocumentVersionEntity.create(41L, 4L, "原始😃正文", "审批", "APPROVAL_MERGE", "HUMAN", 501L, 91L, 61L, null, StableSnapshotUtils.sha256Utf8("原始😃正文"));
        when(versions.approvalSnapshots(41L, 91L)).thenReturn(List.of(merged));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void computesOnlyFrozenProposalAgainstItsOwnMergeAndIgnoresLaterHumanEdits() {
        var first = service.get(91L); document.setContent("又一次编辑"); document.setVersion(13L);
        assertThat(service.get(91L)).isEqualTo(first);
        assertThat(OnlineApprovalDifferenceUtils.valid(first)).isTrue();
        assertThat(first.addedCharacters()).isEqualTo(1); assertThat(first.deletedCharacters()).isEqualTo(1);
        assertThat(first.humanModificationRatio()).isEqualByComparingTo("0.3333333333");
        assertThat(first.originalContentHash()).isEqualTo(StableSnapshotUtils.sha256Utf8("原始😀正文"));
        var roundtrip = JsonUtils.parse(JsonUtils.toJson(first), OnlineApprovalDifferenceVO.class);
        assertThat(OnlineApprovalDifferenceUtils.valid(roundtrip)).isTrue();
        assertThat(OnlineApprovalDifferenceUtils.identityHash(roundtrip)).isEqualTo(first.identityHash());
        var changed = JsonUtils.parse(JsonUtils.toJson(first).replace("\"addedCharacters\":1", "\"addedCharacters\":2"), OnlineApprovalDifferenceVO.class);
        assertThat(OnlineApprovalDifferenceUtils.valid(changed)).isFalse();
        verify(mapper, never()).update(any(), any()); verify(versions, never()).createApprovalSnapshot(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void missingOrMultipleFinalSnapshotsNeverFillZero() {
        when(versions.approvalSnapshots(41L, 91L)).thenReturn(List.of());
        var missing = service.get(91L); assertThat(missing.reason()).isEqualTo("MERGED_EVIDENCE_UNAVAILABLE"); assertThat(missing.addedCharacters()).isNull();
        when(versions.approvalSnapshots(41L, 91L)).thenReturn(List.of(merged, merged));
        assertThat(service.get(91L).reason()).isEqualTo("MULTIPLE_SOURCES");
    }
    @Test void wrongTaskVersionActorOrHashIsNotComparable() {
        merged.setSourceTaskId(99L); assertThat(service.get(91L).reason()).isEqualTo("MERGED_EVIDENCE_INVALID"); merged.setSourceTaskId(61L);
        merged.setVersionNo(5L); assertThat(service.get(91L).reason()).isEqualTo("MERGED_EVIDENCE_INVALID"); merged.setVersionNo(4L);
        merged.setActorId(999L); assertThat(service.get(91L).reason()).isEqualTo("MERGED_EVIDENCE_INVALID"); merged.setActorId(501L);
        merged.setContent("篡改历史正文"); assertThat(service.get(91L).reason()).isEqualTo("MERGED_EVIDENCE_INVALID");
    }
    @Test void unsafeOriginalStructureAndDiffLimitAreDifferentMissingReasons() {
        source = new OnlineApprovalSourceVO(source.input(), "91", "MERGED", source.originalEvidenceHash(), source.originalIdentityHash(), 3L, List.of(), 4L, "501", source.mergedAt(), null);
        when(tasks.getOnlineApprovalSource(91L)).thenReturn(Result.ok(source));
        assertThat(service.get(91L).reason()).isEqualTo("ORIGINAL_EVIDENCE_INVALID");
        source = new OnlineApprovalSourceVO(source.input(), "91", "MERGED", source.originalEvidenceHash(), source.originalIdentityHash(), 3L,
                List.of(new ChangeItemDTO(ChangeOp.REPLACE, null, "a".repeat(2000))), 4L, "501", source.mergedAt(), null);
        when(tasks.getOnlineApprovalSource(91L)).thenReturn(Result.ok(source)); merged.setContent("b".repeat(2000)); merged.setContentSha256(StableSnapshotUtils.sha256Utf8(merged.getContent()));
        var value = service.get(91L); assertThat(value.reason()).isEqualTo("DIFF_LIMIT_EXCEEDED"); assertThat(value.humanModificationRatio()).isNull(); assertThat(OnlineApprovalDifferenceUtils.valid(value)).isTrue();
    }
    @Test void unmergedSourceIsPreservedWithoutReadingHistoricalBodies() {
        source = new OnlineApprovalSourceVO(source.input(), "91", "APPROVED", source.originalEvidenceHash(), source.originalIdentityHash(), 3L, null, null, null, null, "NOT_MERGED");
        when(tasks.getOnlineApprovalSource(91L)).thenReturn(Result.ok(source));
        var value = service.get(91L); assertThat(value.reason()).isEqualTo("NOT_MERGED"); assertThat(OnlineApprovalDifferenceUtils.valid(value)).isTrue(); verifyNoInteractions(versions);
    }
    @Test void currentPermissionAndDocumentSpaceAreRequiredBeforeHistoricalContentAccess() {
        document.setSpaceId(12L); assertThatThrownBy(() -> service.get(91L)).isInstanceOf(BusinessException.class); verifyNoInteractions(versions);
        document.setSpaceId(10L); doThrow(new BusinessException(40300, "权限撤回")).when(permissions).requirePermission(10L, "document:read");
        assertThatThrownBy(() -> service.get(91L)).isInstanceOf(BusinessException.class); verifyNoInteractions(versions);
    }
    @Test void taskAuthorizationFailureAndMismatchedRequestDoNotAccessDocument() {
        when(tasks.getOnlineApprovalSource(91L)).thenReturn(Result.fail(40300, "访问拒绝"));
        assertThatThrownBy(() -> service.get(91L)).isInstanceOf(BusinessException.class); verifyNoInteractions(mapper, versions);
        when(tasks.getOnlineApprovalSource(92L)).thenReturn(Result.ok(source));
        assertThatThrownBy(() -> service.get(92L)).isInstanceOf(BusinessException.class); verifyNoInteractions(mapper, versions);
    }
}
