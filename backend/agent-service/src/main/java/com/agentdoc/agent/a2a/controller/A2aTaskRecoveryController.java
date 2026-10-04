package com.agentdoc.agent.a2a.controller;

import com.agentdoc.agent.a2a.service.A2aTaskRecoveryService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.feign.vo.TaskRecoveryRemoteVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Task;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.agentdoc.common.enums.ErrorCode;

/** 只接受专属恢复头，所有资源核验由应用服务负责。 */
@RestController
@RequiredArgsConstructor
public class A2aTaskRecoveryController {
    private final A2aTaskRecoveryService service;

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Result<Void>> invalidCapability() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ErrorCode.UNAUTHORIZED));
    }

    @Operation(summary = "内部查询既有 A2A Task 用于终态恢复")
    @GetMapping("/api/agent/internal/a2a/tasks/{a2aTaskId}/recovery")
    public Result<TaskRecoveryRemoteVO<Task>> get(@PathVariable String a2aTaskId,
                                                 @RequestHeader(TaskRecoveryConstant.CAPABILITY_HEADER) String token)
            throws A2AError {
        return Result.ok(service.recover(a2aTaskId, token, false));
    }

    @Operation(summary = "内部恢复已经请求取消的既有 A2A Task")
    @PostMapping("/api/agent/internal/a2a/tasks/{a2aTaskId}/recovery-cancel")
    public Result<TaskRecoveryRemoteVO<Task>> cancel(@PathVariable String a2aTaskId,
                                                    @RequestHeader(TaskRecoveryConstant.CAPABILITY_HEADER) String token)
            throws A2AError {
        return Result.ok(service.recover(a2aTaskId, token, true));
    }
}
