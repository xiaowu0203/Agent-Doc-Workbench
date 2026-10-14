package com.agentdoc.document.service;

import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewBatchDTO;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewDTO;
import com.agentdoc.common.utils.StableSnapshotUtils;
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

class DocumentOnlineChangePreviewTest {
    private final DocumentMapper mapper = mock(DocumentMapper.class);
    private final DocumentVersionService versions = mock(DocumentVersionService.class);
    private final SpacePermissionService permissions = mock(SpacePermissionService.class);
    private final DocumentService service = new DocumentService(mapper,mock(DocumentDirectoryService.class),versions,permissions,mock(AuthFeign.class));
    private final String original = " 冻结😀\r\n正文 ";
    private DocumentEntity doc;
    private DocumentVersionEntity base;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","RS256").subject("501").claim("scope","user").build()));
        doc = new DocumentEntity(); doc.setId(41L); doc.setSpaceId(10L); doc.setVersion(12L); doc.setContent("审批后正文"); when(mapper.selectById(41L)).thenReturn(doc);
        base = new DocumentVersionEntity(); base.setContent(original); base.setContentSha256(StableSnapshotUtils.sha256Utf8(original)); when(versions.requireVersion(41L,3L)).thenReturn(base);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private OnlineProposalPreviewBatchDTO request(OnlineProposalPreviewDTO... proposals) {
        return new OnlineProposalPreviewBatchDTO(41L,3L,StableSnapshotUtils.sha256Utf8(original),List.of(proposals));
    }
    @Test void originalPreviewIsUnchangedAfterApprovalEditsAndLoadsFrozenVersionOnceForWholeBatch() {
        var request = request(new OnlineProposalPreviewDTO(91L,3L,List.of(new ChangeItemDTO(ChangeOp.APPEND,null," 原提案 "))),
                new OnlineProposalPreviewDTO(92L,3L,List.of(new ChangeItemDTO(ChangeOp.REPLACE,original,"第二份原始提案"))));
        var first = service.previewOnlineChanges(request); doc.setContent("又一次审批后编辑"); doc.setVersion(13L);
        assertThat(service.previewOnlineChanges(request)).isEqualTo(first);
        assertThat(first.getFirst().contentHash()).isEqualTo(StableSnapshotUtils.sha256Utf8(original + " 原提案 "));
        verify(versions,times(2)).requireVersion(41L,3L); verify(mapper,never()).update(any(),any());
    }
    @Test void completeBadStructureAndWrongBaselineReturnFalseRatherThanMissingZero() {
        var result = service.previewOnlineChanges(request(new OnlineProposalPreviewDTO(91L,3L,List.of()),
                new OnlineProposalPreviewDTO(92L,4L,List.of(new ChangeItemDTO(ChangeOp.REPLACE,null,"内容")))));
        assertThat(result).allMatch(item -> !item.valid() && item.contentHash() == null);
    }
    @Test void unavailableOrCorruptedFrozenInputDoesNotFallBackToCurrentDocument() {
        base.setContent("篡改输入");
        assertThatThrownBy(() -> service.previewOnlineChanges(request(new OnlineProposalPreviewDTO(91L,3L,List.of(new ChangeItemDTO(ChangeOp.REPLACE,null,"内容"))))))
                .isInstanceOf(BusinessException.class);
    }
    @Test void currentReadPermissionIsRequiredBeforeFrozenContentAccess() {
        doThrow(new BusinessException(40300,"权限撤回")).when(permissions).requirePermission(10L,"document:read");
        assertThatThrownBy(() -> service.previewOnlineChanges(request(new OnlineProposalPreviewDTO(91L,3L,List.of())))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(versions);
    }
    @Test void fullReviewPreservesWhitespaceAndReturnsInvalidItemsAlongsideGoodBodies() {
        var result = service.previewOnlineChangesForReview(request(new OnlineProposalPreviewDTO(91L,3L,List.of(new ChangeItemDTO(ChangeOp.APPEND,null," 原始😀\r\n "))),
                new OnlineProposalPreviewDTO(92L,3L,List.of()), new OnlineProposalPreviewDTO(93L,4L,List.of(new ChangeItemDTO(ChangeOp.REPLACE,null,"错基线")))));
        assertThat(result.inputContent()).isEqualTo(original); assertThat(result.items()).hasSize(3);
        assertThat(result.items().getFirst().content()).isEqualTo(original + " 原始😀\r\n ");
        assertThat(result.items().getFirst().contentHash()).isEqualTo(StableSnapshotUtils.sha256Utf8(result.items().getFirst().content()));
        assertThat(result.items().subList(1,3)).allMatch(item -> !item.valid() && item.content() == null && item.contentHash() == null);
        verify(versions,times(1)).requireVersion(41L,3L); verify(mapper,never()).update(any(),any());
    }
    @Test void reviewCannotTurnOversizedContentIntoInvalidItemOrTruncatedOutput() {
        var large = new OnlineProposalPreviewDTO(91L,3L,List.of(new ChangeItemDTO(ChangeOp.REPLACE,null,"x".repeat(2_000_001))));
        assertThatThrownBy(() -> service.previewOnlineChangesForReview(request(large))).isInstanceOf(BusinessException.class).hasMessageContaining("上限");
        assertThat(service.previewOnlineChanges(request(large)).getFirst().valid()).isTrue();
        var item = new OnlineProposalPreviewDTO(91L,3L,List.of(new ChangeItemDTO(ChangeOp.REPLACE,null,"x".repeat(2_000_000))));
        var many = new OnlineProposalPreviewBatchDTO(41L,3L,base.getContentSha256(),List.of(item,
                new OnlineProposalPreviewDTO(92L,3L,item.changes()),new OnlineProposalPreviewDTO(93L,3L,item.changes()),new OnlineProposalPreviewDTO(94L,3L,item.changes())));
        assertThatThrownBy(() -> service.previewOnlineChangesForReview(many)).isInstanceOf(BusinessException.class).hasMessageContaining("上限");
    }
}
