package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.IdentityBindingCreateDTO;
import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/** 管理员对现有业务用户进行明确预绑定。 */
@Tag(name = "外部身份绑定")
@RestController
@RequestMapping("/api/platform/users/{userId}/identities")
@RequireLogin
@PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
@RequiredArgsConstructor
public class IdentityBindingController {
    private final IdentityBindingService bindingService;

    @PostMapping
    @Operation(summary = "预绑定经管理员核验的外部身份")
    public Result<Void> bind(@PathVariable Long userId, @Valid @RequestBody IdentityBindingCreateDTO dto) {
        bindingService.bind(dto.issuer(), dto.subject(), userId);
        return Result.ok();
    }

    @PostMapping("/revoke")
    @Operation(summary = "撤销外部身份绑定并保留历史")
    public Result<Void> revoke(@PathVariable Long userId, @Valid @RequestBody IdentityBindingCreateDTO dto) {
        bindingService.revoke(dto.issuer(), dto.subject(), userId);
        return Result.ok();
    }
}
