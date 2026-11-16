package com.agentdoc.auth.handler;

import com.agentdoc.auth.controller.IdentitySelfController;
import com.agentdoc.auth.controller.UserAuthenticationController;
import com.agentdoc.auth.controller.LocalCredentialController;
import com.agentdoc.auth.controller.LocalPasswordController;
import com.agentdoc.auth.controller.PlatformUserController;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** 认证投影和撤销写入的必需存储故障返回503，不暴露SQL、连接信息或凭据。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {IdentitySelfController.class, UserAuthenticationController.class, LocalCredentialController.class,
        LocalPasswordController.class, PlatformUserController.class})
public class AuthenticationDependencyExceptionHandler {

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Result<Void>> unavailable(RuntimeException error) {
        log.warn("认证存储不可用 type={}", error.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.fail(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> rejected(BusinessException error) {
        return ResponseEntity.status(error.getHttpStatus()).body(Result.fail(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> malformed() {
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST));
    }
}
