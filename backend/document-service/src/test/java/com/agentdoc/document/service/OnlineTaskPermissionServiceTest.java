package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OnlineTaskPermissionServiceTest {
    @Test void initialFormalVersionRetainsProposalOnlyActions() {
        verifyInitialVersion(DocType.FORMAL, JwtConstant.ACTION_CREATE_CHANGE_REQUEST);
    }

    @Test void initialDraftVersionRetainsDraftWriteActions() {
        verifyInitialVersion(DocType.DRAFT, JwtConstant.ACTION_WRITE_DRAFT);
    }

    private void verifyInitialVersion(DocType type, String writeAction) {
        var verifier = mock(OnlineCapabilityVerifier.class);
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("verifier", verifier);
        var evaluation = mock(OnlineEvaluationFeign.class);
        var permissions = mock(SpacePermissionService.class);
        var documents = mock(DocumentService.class);
        var service = new OnlineTaskPermissionService(beans.getBeanProvider(OnlineCapabilityVerifier.class), evaluation,
                permissions, documents, mock(TaskCapabilityVerifier.class));
        var binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
                "50", "10", "20", "30", "40", "0", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
                "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
        var document = new DocumentEntity(); document.setId(40L); document.setSpaceId(20L);
        document.setStatus(DocStatus.NORMAL.getCode()); document.setDocType(type.getCode());
        when(evaluation.waitBinding("10", "wait")).thenReturn(Result.ok(binding));
        when(documents.requireDoc(40L)).thenReturn(document);

        assertThat(service.actions("10", "wait")).containsExactly(JwtConstant.ACTION_READ_FRAGMENT, writeAction);
        verify(verifier).verifyWait("wait", "10");
        verify(permissions).requireOnlineTaskCreatePermission(20L, 50L);
        verify(documents).requireOnlineFrozenVersion(40L, 0L, "b".repeat(64));
    }
}
