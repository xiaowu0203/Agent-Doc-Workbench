package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 任务", description = "任务创建、查询和终止")
@RestController
@RequestMapping("/api/task/tasks")
@RequireLogin
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "创建 Agent 任务")
    @PostMapping
    public Result<TaskVO> create(@Valid @RequestBody TaskCreateDTO dto) {
        return Result.ok(taskService.create(dto));
    }

    @Operation(summary = "分页查询任务")
    @GetMapping
    public Result<PageVO<TaskVO>> list(@RequestParam Long spaceId, PageParam pageParam) {
        return Result.ok(taskService.list(spaceId, pageParam));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<TaskVO> detail(@PathVariable Long id) {
        return Result.ok(taskService.detail(id));
    }

    @Operation(summary = "终止任务")
    @PutMapping("/{id}/terminate")
    public Result<TaskVO> terminate(@PathVariable Long id) {
        return Result.ok(taskService.terminate(id));
    }
}
