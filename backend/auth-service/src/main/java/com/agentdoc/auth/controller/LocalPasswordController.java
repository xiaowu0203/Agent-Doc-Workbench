package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 本人改密仅在local模式装配，始终由当前可信人类身份定位账号。 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalPasswordController {
    private final AuthService authentication;

    @PutMapping("/api/auth/password")
    @Operation(summary = "修改本人的本地密码并撤销旧认证")
    public Result<Void> change(@Valid @RequestBody ChangePasswordRequestDTO request) {
        authentication.changePassword(request); return Result.ok();
    }
}
