package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 仅用已验证的人类Bearer查询该持有者自身身份，不能指定其他用户。
 * <p>
 * Feign客户端，对接auth-service身份查询接口；
 * 入参Authorization头携带当前模式已经验签通过的人类Token，只能查询持有者本人，
 * 不支持传入其他用户ID/Subject去查询别人，防止越权查身份。
 * <p>
 * OIDC模式下完成外部OIDC sub -> 平台内部userId映射。
 */
@FeignClient(name = "auth-identity-self", url = "${agent-doc.feign.identity-auth-url:http://localhost:8081}")
public interface AuthIdentityFeign {

    /**
     * 查询当前token对应的用户身份
     * @param authorization Authorization头，格式为 Bearer xxx
     * @return 用户身份信息：userId、平台角色等
     */
    @GetMapping("/api/auth/identity")
    Result<IdentitySelfVO> currentIdentity(@RequestHeader("Authorization") String authorization);

    /** 使用同一人类Bearer实时复核持有者的平台角色；不能指定其他用户。 */
    @GetMapping("/api/auth/internal/platform-role")
    Result<Void> checkPlatformRole(@RequestHeader("Authorization") String authorization, @RequestParam String roleKey);
}
