package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.param.TaskActivitySearchParam;
import com.agentdoc.task.pojo.param.TaskCreateOptionsParam;
import com.agentdoc.task.pojo.param.TaskSearchParam;
import com.agentdoc.task.pojo.vo.TaskActivityVO;
import com.agentdoc.task.pojo.vo.TaskCreateOptionsVO;
import com.agentdoc.task.pojo.vo.TaskListItemVO;
import com.agentdoc.task.pojo.vo.TaskStatsVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.pojo.vo.TaskExecutionDetailVO;
import com.agentdoc.task.service.TaskService;
import com.agentdoc.task.service.TaskExecutionQueryService;
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
    private final TaskExecutionQueryService taskExecutionQueryService;

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

    @Operation(summary = "分页筛选任务列表")
    @PostMapping("/search")
    public Result<PageVO<TaskListItemVO>> search(@Valid @RequestBody TaskSearchParam param) {
        return Result.ok(taskService.search(param));
    }

    @Operation(summary = "查询新建任务可用选项")
    @PostMapping("/create-options")
    public Result<TaskCreateOptionsVO> createOptions(@Valid @RequestBody TaskCreateOptionsParam param) {
        return Result.ok(taskService.getCreateOptions(param));
    }

    @Operation(summary = "查询任务执行动态")
    @PostMapping("/activity/query")
    public Result<PageVO<TaskActivityVO>> activity(@Valid @RequestBody TaskActivitySearchParam param) {
        return Result.ok(taskService.listActivity(param));
    }

    @Operation(summary = "查询空间任务数量统计")
    @GetMapping("/stats")
    public Result<TaskStatsVO> stats(@RequestParam Long spaceId) {
        return Result.ok(taskService.getStats(spaceId));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{id}")
    public Result<TaskVO> detail(@PathVariable Long id) {
        return Result.ok(taskService.detail(id));
    }

    @Operation(summary = "手动触发待运行任务")
    @PutMapping("/{id}/run")
    public Result<TaskVO> run(@PathVariable Long id) {
        return Result.ok(taskService.run(id));
    }

    @Operation(summary = "查询任务执行详情与脱敏调用轨迹")
    @GetMapping("/{id}/execution-detail")
    public Result<TaskExecutionDetailVO> executionDetail(@PathVariable Long id) {
        return Result.ok(taskExecutionQueryService.detail(id));
    }

    @Operation(summary = "终止任务")
    @PutMapping("/{id}/terminate")
    public Result<TaskVO> terminate(@PathVariable Long id) {
        return Result.ok(taskService.terminate(id));
    }

    @Operation(summary = "重新运行异常任务")
    @PutMapping("/{id}/rerun")
    public Result<TaskVO> rerun(@PathVariable Long id) {
        return Result.ok(taskService.rerun(id));
    }
}
