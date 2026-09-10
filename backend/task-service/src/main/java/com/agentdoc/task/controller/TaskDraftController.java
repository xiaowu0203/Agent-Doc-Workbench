package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.dto.TaskDraftSaveDTO;
import com.agentdoc.task.pojo.param.TaskDraftSearchParam;
import com.agentdoc.task.pojo.vo.TaskDraftVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.service.TaskDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "任务草稿", description = "当前用户的新建任务表单草稿")
@RestController
@RequestMapping("/api/task/task-drafts")
@RequireLogin
@RequiredArgsConstructor
public class TaskDraftController {

    private final TaskDraftService taskDraftService;

    @Operation(summary = "创建任务草稿")
    @PostMapping
    public Result<TaskDraftVO> create(@Valid @RequestBody TaskDraftSaveDTO dto) {
        return Result.ok(taskDraftService.create(dto));
    }

    @Operation(summary = "更新任务草稿")
    @PutMapping("/{id}")
    public Result<TaskDraftVO> update(@PathVariable Long id, @Valid @RequestBody TaskDraftSaveDTO dto) {
        return Result.ok(taskDraftService.update(id, dto));
    }

    @Operation(summary = "查询任务草稿详情")
    @GetMapping("/{id}")
    public Result<TaskDraftVO> detail(@PathVariable Long id) {
        return Result.ok(taskDraftService.detail(id));
    }

    @Operation(summary = "分页查询当前用户的任务草稿")
    @PostMapping("/search")
    public Result<PageVO<TaskDraftVO>> search(@Valid @RequestBody TaskDraftSearchParam param) {
        return Result.ok(taskDraftService.search(param));
    }

    @Operation(summary = "删除任务草稿")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskDraftService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "从草稿启动任务")
    @PostMapping("/{id}/launch")
    public Result<TaskVO> launch(@PathVariable Long id) {
        return Result.ok(taskDraftService.launch(id));
    }
}
