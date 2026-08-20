package com.agentdoc.common.web;

import com.agentdoc.common.api.ErrorCode;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一转换为 Result 结构。
 * 仅依赖 spring-web（ErrorResponse 接口可兜住 NoResourceFoundException / ResponseStatusException 等），
 * 由 common-web-spring-boot-starter（CommonWebAutoConfiguration）自动装配注册。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.VALIDATION_FAILED, msg));
    }

    /**
     * 兜底处理：实现 {@link ErrorResponse} 的异常（NoResourceFoundException / ResponseStatusException 等）
     * 按 HTTP 状态映射错误码；其余按 500 处理。
     * 仅依赖 spring-web，不引用 webmvc 类，确保 WebFlux gateway 加载该 Advice 也安全。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            ErrorCode code = switch (status) {
                case 400, 415 -> ErrorCode.BAD_REQUEST;
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 403 -> ErrorCode.FORBIDDEN;
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                case 409 -> ErrorCode.CONFLICT;
                case 429 -> ErrorCode.TOO_MANY_REQUESTS;
                default -> ErrorCode.INTERNAL_ERROR;
            };
            log.warn("HTTP 异常: {} - {}", status, ex.getMessage());
            return ResponseEntity.status(status).body(Result.fail(code));
        }
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}
