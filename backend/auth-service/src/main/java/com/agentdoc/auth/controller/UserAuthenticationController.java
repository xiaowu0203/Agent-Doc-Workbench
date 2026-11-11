package com.agentdoc.auth.controller;

import com.agentdoc.auth.service.PlatformUserService;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/** 用户级强退同时适用于本地认证版本与OIDC认证时间边界。 */
@Tag(name = "用户认证撤销")
@RestController
@RequestMapping("/api/platform/users")
@PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
@RequiredArgsConstructor
public class UserAuthenticationController {

    private final PlatformUserService users;

    @Operation(summary = "Workbench本地全端强退；不注销其他SSO应用")
    @PostMapping("/{userId}/auth-revocations")
    public Result<Void> revoke(@PathVariable Long userId) {
        users.forceLogout(userId);
        return Result.ok();
    }
}
