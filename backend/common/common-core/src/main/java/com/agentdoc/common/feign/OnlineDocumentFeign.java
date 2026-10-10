package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.HttpHeaders;

/** Document 对签名授权人的当前权限复查，不建立人类身份。 */
@FeignClient(name = "online-document", url = "${agent-doc.online.document-url:http://localhost:8082}")
public interface OnlineDocumentFeign {
    @GetMapping("/api/document/internal/online-authorizations/{experimentId}/resources")
    Result<Void> requireResources(@PathVariable String experimentId, @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    @PostMapping("/api/document/internal/online-authorizations/{experimentId}/scoped-permission/{purpose}")
    Result<Void> scopedProtectionPermission(@PathVariable String experimentId, @PathVariable OnlineCapabilityPurpose purpose,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String token, @RequestBody List<String> taskIds);
    @GetMapping("/api/document/internal/online-release")
    Result<String> release(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    @GetMapping("/api/document/internal/online-authorization-tasks/{taskId}/execution-actions")
    Result<List<String>> executionActions(@PathVariable String taskId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
    @GetMapping("/api/document/internal/online-authorization-tasks/{taskId}/actions")
    Result<List<String>> waitActions(@PathVariable String taskId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    /** 初次签发使用当前登录用户，批量裁决受保护 OWNER 和两项保护动作。 */
    @GetMapping("/api/document/internal/online-authorizations/spaces/{spaceId}/human-permission")
    Result<Void> requireHumanProtectionPermission(@PathVariable String spaceId);

    @GetMapping("/api/document/internal/online-authorizations/{experimentId}/permission")
    Result<Void> requireProtectionPermission(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
}
