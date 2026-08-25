package com.agentdoc.task.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 任务内部接口", description = "令牌-任务校验是否匹配")
@RestController
@RequestMapping("/api/task/internal/tasks")
public class InternalTaskController {

    private final TaskService taskService;

    public InternalTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "校验X‑TASK‑CAPABILITY任务能力令牌是否对指定taskId业务有效")
    @GetMapping("/{taskId}/capability")
    public Result<Void> checkCapability(
            @PathVariable Long taskId,
            @RequestHeader(value = HeaderConstants.X_TASK_CAPABILITY, required = false) String token) {
        taskService.checkCapability(taskId, token);
        return Result.ok();
    }
}
