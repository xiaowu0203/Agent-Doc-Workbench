package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.task.service.TaskOriginalEvidenceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequiredArgsConstructor
public class TaskOriginalEvidencePermissionController {
    private final TaskOriginalEvidenceAccessService service;
    @Operation(summary = "复核原始产物证据的当前 Task 和文档访问权限")
    @GetMapping("/api/task/tasks/{taskId}/original-evidence-permission")
    public Result<Void> require(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        service.require(taskId, spaceId, executionId);
        return Result.ok();
    }
}
