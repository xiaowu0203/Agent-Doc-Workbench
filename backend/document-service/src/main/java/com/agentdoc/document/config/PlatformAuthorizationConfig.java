package com.agentdoc.document.config;

import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.security.PlatformRoleVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 为业务服务装配平台角色远程复核器；Auth自身保持本地复核。 */
@Configuration(proxyBeanMethods = false)
public class PlatformAuthorizationConfig {
    @Bean
    public PlatformRoleVerifier platformRoleVerifier(AuthIdentityFeign identity) {
        return new PlatformRoleVerifier(identity);
    }
}
