package com.agentdoc.common.feign.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

/**
 * 当前用户身份查询的错误分类；不复制远程响应、URL或凭据。
 * Feign调用auth-identity-self身份服务时的错误解码器，对非2xx响应做异常转换
 */
public class IdentitySelfFeignErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 403 && methodKey.startsWith("AuthIdentityFeign#checkPlatformRole(")) {
            return new AccessDeniedException("缺少平台角色");
        }
        // 401未授权 / 403权限拒绝：转换为凭证无效异常
        if (response.status() == 401 || response.status() == 403) {
            return new BadCredentialsException("用户身份不可用");
        }
        // 其余错误统一包装为身份服务不可用异常
        return new AuthenticationServiceException("身份服务不可用");
    }
}
