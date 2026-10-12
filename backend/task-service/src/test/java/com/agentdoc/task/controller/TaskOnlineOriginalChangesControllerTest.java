package com.agentdoc.task.controller;

import com.agentdoc.task.service.TaskOnlineOriginalChangesService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskOnlineOriginalChangesControllerTest {
    @Test void originalJsonIsNeverCacheable() throws Exception {
        var service = mock(TaskOnlineOriginalChangesService.class);
        MockMvcBuilders.standaloneSetup(new TaskOnlineOriginalChangesController(service)).build()
                .perform(get("/api/task/tasks/61/online-original-proposal-payloads").param("spaceId","10").param("executionId","71"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).payloads(61L,10L,71L);
    }
}
