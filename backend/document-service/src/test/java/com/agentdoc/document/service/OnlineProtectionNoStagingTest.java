package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineProtectionNoStagingTest {
    private final SpacePermissionService permissions = mock(SpacePermissionService.class);
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final DocumentEntity doc = new DocumentEntity();
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
            "50", "10", "20", "30", "40", "0", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
            "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private OnlineProtectionPermissionService fixture() {
        doc.setId(40L); doc.setSpaceId(20L); doc.setStatus(DocStatus.NORMAL.getCode()); doc.setDocType(DocType.DRAFT.getCode());
        when(evaluation.humanBinding("10")).thenReturn(Result.ok(binding)); when(documents.requireDoc(40L)).thenReturn(doc);
        return new OnlineProtectionPermissionService(new DefaultListableBeanFactory().getBeanProvider(OnlineCapabilityVerifier.class), permissions, evaluation, documents);
    }
    private void check(OnlineProtectionPermissionService service, long actor) {
        try (var user = mockStatic(AuthUtils.class)) { user.when(AuthUtils::getUserIdOrException).thenReturn(actor); service.requireNoDraftStaging("10"); }
    }
    @Test void noStagingProofRetainsCurrentOwnerEditAndFrozenVersionChecks() {
        var service = fixture(); check(service, 50L);
        verify(permissions).requireOnlineProtectionPermission(20L, 50L); verify(permissions).requirePermission(20L, DOCUMENT_EDIT);
        verify(documents).requireOnlineFrozenVersion(40L, 0L, "b".repeat(64));
        verify(documents, never()).finalizeAgentDraftChanges(any()); verify(documents, never()).discardAgentDraftChanges(any());
    }
    @ParameterizedTest @ValueSource(strings = {"task", "base", "revision", "content"})
    void anyStagingOrCorruptPartialStagingBlocksReceipt(String field) {
        var service = fixture();
        switch (field) { case "task" -> doc.setAgentStagedTaskId(99L); case "base" -> doc.setAgentStagedBaseVersion(0L);
            case "revision" -> doc.setAgentStagedRevision(1L); case "content" -> doc.setAgentStagedContent("staged"); }
        assertThatThrownBy(() -> check(service, 50L)).hasMessageContaining("DRAFT_VERSION_CONFLICT");
    }
    @Test void nonCreatorCannotUseOwnerPrivilegeForAnotherActorsTask() {
        var service = fixture(); assertThatThrownBy(() -> check(service, 51L)).hasMessageContaining("RECOVERY_IDENTITY_MISMATCH");
        verifyNoInteractions(permissions, documents);
    }
    @Test void currentPermissionRevocationFailsBeforeReadingDocument() {
        var service = fixture(); doThrow(new BusinessException(ErrorCode.FORBIDDEN, "OWNER_REQUIRED"))
                .when(permissions).requireOnlineProtectionPermission(20L, 50L);
        assertThatThrownBy(() -> check(service, 50L)).hasMessageContaining("OWNER_REQUIRED"); verifyNoInteractions(documents);
    }
    @Test void changedDocumentScopeCannotReturnNoStagingProof() {
        var service = fixture(); doc.setSpaceId(21L);
        assertThatThrownBy(() -> check(service, 50L)).hasMessageContaining("RECOVERY_IDENTITY_MISMATCH");
        verify(documents, never()).requireOnlineFrozenVersion(any(), any(), any());
    }
}
