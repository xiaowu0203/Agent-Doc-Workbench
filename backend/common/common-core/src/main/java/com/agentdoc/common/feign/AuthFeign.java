package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Auth 服务内部认证契约。
 */
@FeignClient(name = "auth-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface AuthFeign {
    /**
     * 内部签发任务能力令牌（A2A、MCP）
     * @param request 签发请求
     * @return 凭证
     */
    @PostMapping("/api/auth/internal/task-capabilities")
    Result<String> issueTaskCapability(@RequestBody TaskCapabilityIssueDTO request);

    /**
     * 校验当前用户是否拥有roleKey角色
     * @param roleKey 平台角色标识
     * @return 校验成功结果
     */
    @GetMapping("/api/auth/internal/platform-role")
    Result<Void> checkPlatformRole(@RequestParam String roleKey);
}
