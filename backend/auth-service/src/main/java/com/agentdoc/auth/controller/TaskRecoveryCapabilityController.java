package com.agentdoc.auth.controller;

import com.agentdoc.auth.service.TaskRecoveryIssuanceService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.feign.dto.TaskRecoveryIssueDTO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 专属机器身份入口，公共 Gateway 拒绝转发。 */
@RestController
@RequiredArgsConstructor
public class TaskRecoveryCapabilityController {
    private final TaskRecoveryIssuanceService issuanceService;

    @Operation(summary = "内部签发单 Task 终态恢复窄凭证")
    @PostMapping("/api/auth/internal/task-recovery-capabilities")
    public Result<String> issueRecovery(@RequestHeader(TaskRecoveryConstant.MACHINE_KEY_HEADER) String key,
                                        @RequestBody TaskRecoveryIssueDTO request) {
        return Result.ok(issuanceService.issue(key, request, false));
    }

    @Operation(summary = "内部签发单 Task 既有草稿收尾窄凭证")
    @PostMapping("/api/auth/internal/task-draft-finalization-capabilities")
    public Result<String> issueFinalization(@RequestHeader(TaskRecoveryConstant.MACHINE_KEY_HEADER) String key,
                                            @RequestBody TaskRecoveryIssueDTO request) {
        return Result.ok(issuanceService.issue(key, request, true));
    }
}
