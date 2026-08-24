package com.agentdoc.common.handler;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局统一异常处理器，@RestControllerAdvice，仅对HTTP Controller接口生效。
 * <p>
 * 处理分类：
 * <ul>
 * <li>{@link BusinessException}：业务自定义异常，返回业务错误码；</li>
 * <li>{@link MethodArgumentNotValidException}：JSR‑303参数校验异常，提取第一个字段错误；</li>
 * <li>{@link Exception} 兜底：识别Spring {@link ErrorResponse}体系异常映射业务码；其余作为系统500错误。</li>
 * </ul>
 * <p>兼容性说明：仅依赖 spring‑web 基础API，不强依赖Servlet/WebMvc特有类，
 * 理论上Gateway WebFlux环境也可以加载（但Gateway一般不会导入common‑web starter）。
 * <p>注意：只处理Controller层抛出的异常；Filter、Interceptor抛出的异常，
 * 在Servlet环境下也会进入本Advice；但WebFlux WebFilter抛出异常不会进入此类。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务自定义异常 {@link BusinessException}。
     * @param ex 业务异常
     * @return 统一Result失败响应，HTTP 200，携带业务错误码与消息
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 捕获请求参数校验异常（@Valid / @Validated）。
     * 取第一个字段校验错误作为返回提示。
     * @param ex 参数校验异常
     * @return Result失败响应，HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.VALIDATION_FAILED, msg));
    }

    /**
     * 全局兜底异常捕获。
     * <p>
     * 优先识别Spring实现 {@link ErrorResponse} 的内置异常：
     * {@code NoResourceFoundException、ResponseStatusException} 等，根据HTTP状态码映射业务ErrorCode；
     * 非ErrorResponse类型全部当作系统内部错误500，打印error级别的完整堆栈日志。
     * </p>
     * @param ex 任意未被上面handler捕获的异常
     * @return Result包装的错误响应，携带对应HTTP status与业务错误码
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneric(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            ErrorCode code = switch (status) {
                case BAD_REQUEST, UNSUPPORTED_MEDIA_TYPE -> ErrorCode.BAD_REQUEST;
                case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
                case FORBIDDEN -> ErrorCode.FORBIDDEN;
                case NOT_FOUND -> ErrorCode.NOT_FOUND;
                case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
                case CONFLICT -> ErrorCode.CONFLICT;
                case TOO_MANY_REQUESTS -> ErrorCode.TOO_MANY_REQUESTS;
                case null, default -> ErrorCode.INTERNAL_ERROR;
            };
            log.warn("HTTP 异常: {} - {}", status, ex.getMessage());
            return ResponseEntity.status(errorResponse.getStatusCode()).body(Result.fail(code));
        }
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }
}
