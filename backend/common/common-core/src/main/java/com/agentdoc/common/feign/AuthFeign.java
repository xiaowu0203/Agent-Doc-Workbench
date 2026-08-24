package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Auth 服务内部认证契约。
 */
@FeignClient(name = "auth-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface AuthFeign {

    @PostMapping("/api/auth/internal/task-capabilities")
    Result<String> issueTaskCapability(@RequestBody TaskCapabilityIssueDTO request);
}
