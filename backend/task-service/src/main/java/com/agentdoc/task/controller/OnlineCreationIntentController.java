package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.service.TaskService;
import com.agentdoc.task.service.TaskOnlineRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/task/internal/online-creation-intents")
public class OnlineCreationIntentController {
    private final TaskOnlineRoutingService routing;
    private final TaskService tasks;
    @GetMapping("/{taskId}")
    public Result<OnlineAssignmentRequestDTO> proof(@PathVariable String taskId) {
        tasks.authorizeOnlineCreationProof(OnlineProtocolUtils.id(taskId));
        return Result.ok(routing.proof(taskId));
    }
}
