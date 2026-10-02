package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.task.pojo.vo.TaskTraceViewVO;
import com.agentdoc.task.service.TaskTraceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/task/tasks")
@Tag(name = "Task Trace", description = "按 Task 权限读取受控遥测，不替代业务审计")
public class TaskTraceController {
    private final TaskTraceQueryService queryService;

    @GetMapping("/{id}/trace-view")
    @Operation(summary = "查询 Task 的受控 OTel 诊断视图")
    public Result<TaskTraceViewVO> detail(@PathVariable Long id) {
        return Result.ok(queryService.detail(id));
    }
}
