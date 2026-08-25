package com.agentdoc.task.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.task.a2a.A2aCallbackService;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task/internal/a2a")
public class A2aCallbackController {

    private final A2aCallbackService callbackService;

    public A2aCallbackController(A2aCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Operation(summary = "A2A回调接口")
    @PostMapping("/events")
    public Result<Void> receive(
            @RequestBody JsonNode event,
            @RequestHeader(A2aTaskClient.NOTIFICATION_TOKEN_HEADER) String notificationToken) {
        callbackService.receive(event, notificationToken);
        return Result.ok();
    }
}
