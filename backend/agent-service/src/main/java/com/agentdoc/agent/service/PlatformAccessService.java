package com.agentdoc.agent.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.utils.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 平台级能力访问校验。
 * <p>先校验当前 JWT 的角色投影，再通过 Auth 服务复核实时绑定；任一步失败均拒绝访问。</p>
 */
@Component("PlatformAccess")
@RequiredArgsConstructor
public class PlatformAccessService {

    private final AuthFeign authFeign;

    /**
     * 判断当前用户是否具有指定平台角色。
     *
     * @param roleKey 平台角色稳定标识
     * @return true 表示角色声明和实时绑定均校验通过
     */
    public boolean hasRole(String roleKey) {
        if (roleKey == null || !AuthUtils.hasPlatformRole(roleKey)) {
            return false;
        }
        try {
            Result<Void> result = authFeign.checkPlatformRole(roleKey);
            return result != null && result.code() == ErrorCode.SUCCESS.getCode();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
