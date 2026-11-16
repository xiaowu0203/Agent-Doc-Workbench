package com.agentdoc.auth.controller;

import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.auth.service.PlatformUserService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 只返回当前人类Bearer对应的身份，不接收目标用户参数。 */
@RestController
@RequestMapping("/api/auth/identity")
@RequiredArgsConstructor
public class IdentitySelfController {
    private final PlatformUserService users;
    private final PlatformRoleService roles;

    @GetMapping
    @Operation(summary = "查询当前用户的业务身份")
    public Result<IdentitySelfVO> identity() {
        Long userId = AuthUtils.getUserIdOrException();
        var user = users.requireEnabledUser(userId);
        return Result.ok(new IdentitySelfVO(userId, roles.listRoleKeys(userId), user.getAuthVersion()));
    }
}
