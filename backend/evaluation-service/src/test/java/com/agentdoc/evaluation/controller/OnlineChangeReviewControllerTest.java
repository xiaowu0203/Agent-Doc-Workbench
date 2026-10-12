package com.agentdoc.evaluation.controller;

import com.agentdoc.evaluation.service.OnlineChangeReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OnlineChangeReviewControllerTest {
    @Test void completeOriginalChangesAreNeverCacheable() throws Exception {
        var service = mock(OnlineChangeReviewService.class);
        MockMvcBuilders.standaloneSetup(new OnlineChangeReviewController(service)).build()
                .perform(get("/api/evaluation/online-experiments/11/assignments/51/change-review-context"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).read("11","51");
    }
}
