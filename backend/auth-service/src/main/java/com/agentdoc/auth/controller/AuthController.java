package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.UserRefVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户与既有业务能力接口；浏览器会话由P2的BFF负责。
 */
@Tag(name = "认证", description = "当前用户与受限业务能力")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PlatformRoleService platformRoleService;
    public AuthController(AuthService authService, PlatformRoleService platformRoleService) {
        this.authService = authService;
        this.platformRoleService = platformRoleService;
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }

    @Operation(summary = "内部签发任务能力令牌")
    @PostMapping("/internal/task-capabilities")
    public Result<String> issueTaskCapability(@RequestBody TaskCapabilityIssueDTO request) {
        return Result.ok(authService.issueTaskCapability(request));
    }

    @Operation(summary = "内部签发 Evaluation Worker 能力令牌")
    @PostMapping("/internal/evaluation-worker-capabilities")
    public Result<String> issueEvaluationWorkerCapability(@RequestBody EvaluationWorkerCapabilityIssueDTO request) {
        return Result.ok(authService.issueEvaluationWorkerCapability(request));
    }

    @Operation(summary = "校验当前用户是否拥有roleKey角色（远程调用，目前作用仅仅只是查看是否为平台超级管理员）")
    @GetMapping("/internal/platform-role")
    public Result<Void> checkPlatformRole(@RequestParam String roleKey) {
        platformRoleService.requireCurrentUserRole(roleKey);
        return Result.ok();
    }

    @Operation(summary = "内部批量查询用户展示信息")
    @PostMapping("/internal/users/query")
    public Result<List<UserRefVO>> queryUsers(@RequestBody UserBatchQueryDTO request) {
        return Result.ok(authService.queryUsers(request));
    }
}
