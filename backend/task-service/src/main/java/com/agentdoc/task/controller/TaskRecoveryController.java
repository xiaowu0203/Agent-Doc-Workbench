package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.task.pojo.vo.TaskRecoveryStatusVO;
import com.agentdoc.task.service.TaskRecoveryService;
import com.agentdoc.task.service.TaskOnlineTerminalRepairService;
import com.agentdoc.task.enums.TaskRecoveryReason;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;

/** 人工兜底只暴露脱敏诊断和无载荷触发。 */
@RequireLogin
@RestController
@RequiredArgsConstructor
public class TaskRecoveryController {
    private final TaskRecoveryService service;
    private final TaskOnlineTerminalRepairService onlineRepair;

    @Operation(summary = "读取任务终态恢复诊断")
    @GetMapping("/api/task/tasks/{id}/recovery-status")
    public Result<TaskRecoveryStatusVO> status(@PathVariable Long id) {
        var status = service.status(id);
        return Result.ok(!status.recoveryRequired() && onlineRepair.pending(id) ? new TaskRecoveryStatusVO(status.taskId(), status.taskStatus(), status.configured(),
                true, TaskRecoveryReason.ONLINE_TERMINAL_FINALIZATION_PENDING, status.latestEvent()) : status);
    }

    @Operation(summary = "受审计触发同一 Task 终态恢复，不追加取消意图")
    @PostMapping("/api/task/tasks/{id}/recovery")
    public Result<TaskRecoveryStatusVO> recover(@PathVariable Long id, @RequestBody(required = false) JsonNode body) {
        if (body != null) { throw new BusinessException(ErrorCode.BAD_REQUEST, "人工恢复不接受请求载荷"); }
        var status = service.status(id); // 原 Task 读取权限入口；原凭证过期/损坏仍交给既有恢复策略。
        if (!status.recoveryRequired() && onlineRepair.repair(id)) { return Result.ok(service.status(id)); }
        return Result.ok(service.recoverManually(id));
    }
}
