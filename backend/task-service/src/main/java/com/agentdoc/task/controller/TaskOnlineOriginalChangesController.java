package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineOriginalProposalPayloadVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.agentdoc.task.service.TaskOnlineOriginalChangesService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequiredArgsConstructor
public class TaskOnlineOriginalChangesController {
    private final TaskOnlineOriginalChangesService service;
    @Operation(summary = "读取全部原始提案的不可变载荷")
    @GetMapping("/api/task/tasks/{taskId}/online-original-proposal-payloads")
    public ResponseEntity<Result<OnlineOriginalProposalPayloadVO>> payloads(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(service.payloads(taskId, spaceId, executionId)));
    }
    @Operation(summary = "读取完整线上原始变更集合的校验事实")
    @GetMapping("/api/task/tasks/{taskId}/online-original-changes")
    public Result<OnlineOriginalChangesVO> read(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        return Result.ok(service.read(taskId, spaceId, executionId));
    }
}
