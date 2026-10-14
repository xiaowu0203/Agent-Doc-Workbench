package com.agentdoc.evaluation.controller;

import com.agentdoc.evaluation.service.OnlineTextReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OnlineTextReviewControllerTest {
    @Test void sensitiveOriginalContentIsNeverCacheable() throws Exception {
        var service = mock(OnlineTextReviewService.class);
        MockMvcBuilders.standaloneSetup(new OnlineTextReviewController(service)).build()
                .perform(get("/api/evaluation/online-experiments/11/assignments/51/text-review-context"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).read("11", "51");
    }
}
