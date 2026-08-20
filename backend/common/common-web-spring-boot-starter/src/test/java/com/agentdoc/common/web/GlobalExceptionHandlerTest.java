package com.agentdoc.common.web;

import com.agentdoc.common.api.ErrorCode;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionMapsToResult() {
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(new BusinessException(ErrorCode.LOGIN_FAILED));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), resp.getBody().code());
        assertEquals(ErrorCode.LOGIN_FAILED.getMessage(), resp.getBody().message());
    }

    @Test
    void validationExceptionMapsTo400() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BindException bindException = new BindException(new GlobalExceptionHandlerTest(), "form");
        bindException.addError(new FieldError("form", "username", "用户名不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindException);

        ResponseEntity<Result<Void>> resp = handler.handleValidation(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(ErrorCode.VALIDATION_FAILED.getCode(), resp.getBody().code());
    }

    @Test
    void notFoundExceptionMapsTo404() {
        ResponseEntity<Result<Void>> resp = handler.handleGeneric(
                new NoResourceFoundException(HttpMethod.GET, "/missing"));
        assertEquals(404, resp.getStatusCode().value());
        assertEquals(ErrorCode.NOT_FOUND.getCode(), resp.getBody().code());
    }

    @Test
    void genericExceptionMapsTo500() {
        ResponseEntity<Result<Void>> resp = handler.handleGeneric(new IllegalStateException("boom"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), resp.getBody().code());
    }

    @SuppressWarnings("unused")
    private void validationTarget(String username) {
    }
}
