package com.agentdoc.evaluation.controller;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.pojo.param.EvaluatorPublishedSearchParam;
import com.agentdoc.evaluation.pojo.param.OnlineQualityHistorySearchParam;
import com.agentdoc.evaluation.service.EvaluationCatalogService;
import com.agentdoc.evaluation.service.OnlineBlindReviewService;
import com.agentdoc.evaluation.service.OnlineExperimentService;
import com.agentdoc.evaluation.service.OnlineQualityHistoryService;
import com.agentdoc.evaluation.service.OnlineExperimentReportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OnlineEngineeringReadControllerTest {
    @Test void reportIndexBindsPaginationAndPreservesTextIdentity() throws Exception {
        var service = mock(OnlineExperimentReportService.class);
        MockMvcBuilders.standaloneSetup(new OnlineExperimentReportController(service)).build()
                .perform(get("/api/evaluation/online-experiments/9007199254740993/reports?pageNum=2&pageSize=10"))
                .andExpect(status().isOk());
        var param = ArgumentCaptor.forClass(PageParam.class);
        verify(service).revisions(eq("9007199254740993"), param.capture());
        assertEquals(2, param.getValue().getPageNum()); assertEquals(10, param.getValue().getPageSize());
    }
    @Test void fullFrozenPromptIsNeverCacheable() throws Exception {
        var service = mock(OnlineExperimentService.class);
        MockMvcBuilders.standaloneSetup(new OnlineExperimentController(service)).build()
                .perform(get("/api/evaluation/online-experiments/9007199254740993/prompt-comparison"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).promptComparison("9007199254740993");
    }

    @Test void feedbackHistoryIsNeverCacheableAndKeepsTargetAndPagination() throws Exception {
        var service = mock(OnlineQualityHistoryService.class);
        MockMvcBuilders.standaloneSetup(new OnlineBlindReviewController(mock(OnlineBlindReviewService.class), service)).build()
                .perform(post("/api/evaluation/online-experiments/11/assignments/51/quality-feedback-history/search")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"CHANGE_SET\",\"pageNum\":2,\"pageSize\":10}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var param = ArgumentCaptor.forClass(OnlineQualityHistorySearchParam.class);
        verify(service).search(eq("11"), eq("51"), param.capture());
        assertEquals("CHANGE_SET", param.getValue().getTarget());
        assertEquals(2, param.getValue().getPageNum());
        assertEquals(10, param.getValue().getPageSize());
    }

    @Test void publishedBatchPreservesLongTextIdsAndRejectsNumericJsonIds() {
        var service = mock(EvaluationCatalogService.class);
        var controller = new EvaluationCatalogController(service);
        controller.publishedEvaluatorVersions("{\"spaceId\":\"7\",\"versionIds\":[\"9007199254740993\",\"82\"]}");
        var param = ArgumentCaptor.forClass(EvaluatorPublishedSearchParam.class);
        verify(service).publishedEvaluatorVersions(param.capture());
        assertEquals(List.of("9007199254740993", "82"), param.getValue().getVersionIds());
        clearInvocations(service);
        for (String invalid : List.of("{\"spaceId\":7,\"versionIds\":[\"82\"]}",
                "{\"spaceId\":\"7\",\"versionIds\":[9007199254740993]}",
                "{\"spaceId\":\"7\",\"versionIds\":[null]}",
                "{\"spaceId\":\"7\",\"spaceId\":\"8\",\"versionIds\":[\"82\"]}")) {
            assertThrows(BusinessException.class, () -> controller.publishedEvaluatorVersions(invalid));
        }
        verifyNoInteractions(service);
    }
}
