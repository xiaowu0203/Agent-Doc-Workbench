package com.agentdoc.agent.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.security.PlatformRoleVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 平台级能力访问校验。
 * <p>先校验可信人类角色投影，再直连 Auth 复核实时绑定；依赖故障不降级为无角色。</p>
 */
@Component("PlatformAccess")
@RequiredArgsConstructor
public class PlatformAccessService {

    private final AuthIdentityFeign identity;

    /**
     * 判断当前用户是否具有指定平台角色。
     *
     * @param roleKey 平台角色稳定标识
     * @return true 表示角色声明和实时绑定均校验通过
     */
    public boolean hasRole(String roleKey) {
        return PlatformRoleVerifier.hasRole(identity, roleKey);
    }

    /** 要求当前用户具有指定平台角色。 */
    public void requireRole(String roleKey) {
        if (!hasRole(roleKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要平台超级管理员权限");
        }
    }
}
