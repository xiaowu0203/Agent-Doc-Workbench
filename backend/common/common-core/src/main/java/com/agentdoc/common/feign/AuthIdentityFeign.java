package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 仅用已验证的人类Bearer查询该持有者自身身份，不能指定其他用户。 */
@FeignClient(name = "auth-identity-self", url = "${agent-doc.feign.identity-auth-url:http://localhost:8081}")
public interface AuthIdentityFeign {
    @GetMapping("/api/auth/identity")
    Result<IdentitySelfVO> currentIdentity(@RequestHeader("Authorization") String authorization);
}
