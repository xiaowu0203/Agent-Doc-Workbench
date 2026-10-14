package com.agentdoc.document.controller;

import com.agentdoc.document.service.DocumentService;
import com.agentdoc.document.service.DocumentOnlineOriginalDraftService;
import com.agentdoc.document.service.DocumentOnlineApprovalDifferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentOnlineOriginalChangeControllerTest {
    @Test void completeFrozenInputAndPreviewAreNeverCacheable() throws Exception {
        var service = mock(DocumentService.class);
        MockMvcBuilders.standaloneSetup(new DocumentOnlineOriginalChangeController(service,
                mock(DocumentOnlineOriginalDraftService.class),mock(DocumentOnlineApprovalDifferenceService.class))).build()
                .perform(post("/api/document/documents/online-change-review-preview").contentType("application/json")
                        .content("{\"documentId\":41,\"baseVersion\":3,\"baseContentSha256\":\"hash\",\"proposals\":[]}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).previewOnlineChangesForReview(argThat(request -> request.documentId() == 41L && request.baseVersion() == 3L));
    }
}
