package com.agentdoc.document.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.document.service.OnlineTaskPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.HttpHeaders;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document/internal/online-authorization-tasks")
public class OnlineTaskPermissionController {
    private final OnlineTaskPermissionService service;
    @GetMapping("/{taskId}/execution-actions")
    public Result<List<String>> executionActions(@PathVariable String taskId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!authorization.startsWith("Bearer ")) { throw new BusinessException(ErrorCode.FORBIDDEN); }
        return Result.ok(service.executionActions(taskId, authorization.substring("Bearer ".length())));
    }
    @GetMapping("/{taskId}/actions")
    public Result<List<String>> actions(@PathVariable String taskId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) {
        return Result.ok(service.actions(taskId, wait));
    }
}
