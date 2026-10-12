package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
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
    @Operation(summary = "读取经整体输入摘要核验的冻结用户目标")
    @GetMapping("/api/task/tasks/{taskId}/online-review-goal")
    public ResponseEntity<Result<OnlineReviewGoalVO>> goal(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(service.goal(taskId, spaceId, executionId)));
    }
    @Operation(summary = "读取当前授权的线上原始变更冻结输入投影")
    @GetMapping("/api/task/tasks/{taskId}/online-original-input")
    public Result<OnlineOriginalInputVO> input(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        return Result.ok(service.input(taskId, spaceId, executionId));
    }
    @Operation(summary = "复核原始产物证据的当前 Task 和文档访问权限")
    @GetMapping("/api/task/tasks/{taskId}/original-evidence-permission")
    public Result<Void> require(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        service.require(taskId, spaceId, executionId);
        return Result.ok();
    }
}
