package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.OnlineControlAuthorizeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;

/** 控制服务续签/派生窄凭证；不提供一般任务签发入口。 */
@FeignClient(name = "online-auth", url = "${agent-doc.online.auth-url:http://localhost:8081}")
public interface OnlineAuthFeign {
    @GetMapping("/api/auth/internal/online-release")
    Result<String> release(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    @PostMapping("/api/auth/internal/online-waits/{taskId}/human")
    Result<String> initialWait(@PathVariable String taskId);
    @PostMapping("/api/auth/internal/online-waits/{taskId}/exchange")
    Result<String> exchange(@PathVariable String taskId, @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    @PostMapping("/api/auth/internal/online-waits/{taskId}/renew")
    Result<String> renewWait(@PathVariable String taskId, @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    /** 状态应用层持当前人类凭证申请初次/重新明确授权，结果仅在后端加密保存。 */
    @PostMapping("/api/auth/internal/online-control-authorizations")
    Result<String> authorize(@RequestBody OnlineControlAuthorizeDTO request);

    @PostMapping("/api/auth/internal/online-capabilities")
    Result<String> issue(@RequestHeader(OnlineCapabilityConstant.HEADER) String control,
            @RequestBody OnlineCapabilityIssueDTO request);
}
