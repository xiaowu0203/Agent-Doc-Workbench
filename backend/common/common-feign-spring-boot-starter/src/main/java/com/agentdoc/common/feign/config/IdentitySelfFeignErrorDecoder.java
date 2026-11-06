package com.agentdoc.common.feign.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;

/** 当前用户身份查询的错误分类；不复制远程响应、URL或凭据。 */
public class IdentitySelfFeignErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 401 || response.status() == 403) {
            return new BadCredentialsException("用户身份不可用");
        }
        return new AuthenticationServiceException("身份服务不可用");
    }
}
