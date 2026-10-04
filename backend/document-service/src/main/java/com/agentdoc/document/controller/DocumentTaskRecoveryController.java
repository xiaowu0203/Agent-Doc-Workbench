package com.agentdoc.document.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.document.service.DocumentTaskRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;

/** 不接受正文、终态、替代文档或动作载荷。 */
@RestController
@RequiredArgsConstructor
public class DocumentTaskRecoveryController {
    private final DocumentTaskRecoveryService service;

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Result<Void>> invalidCapability() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ErrorCode.UNAUTHORIZED));
    }

    @Operation(summary = "内部幂等收尾同一 Task 的既有草稿暂存")
    @PostMapping("/api/document/internal/task-drafts/{taskId}/finalize")
    public Result<Void> finalizeDraft(@PathVariable Long taskId,
                                      @RequestHeader(TaskRecoveryConstant.CAPABILITY_HEADER) String token,
                                      @RequestBody(required = false) JsonNode body) {
        if (body != null) { throw new BusinessException(ErrorCode.BAD_REQUEST, "恢复接口不接受请求载荷"); }
        service.finalizeDraft(taskId, token);
        return Result.ok();
    }
}
