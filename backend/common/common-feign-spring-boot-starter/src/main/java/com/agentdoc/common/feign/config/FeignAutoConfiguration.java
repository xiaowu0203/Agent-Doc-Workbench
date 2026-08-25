package com.agentdoc.common.feign.config;

import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * common-feign 自动装配：扫描 common-core 的 feign 契约包并注册 Feign 客户端。
 * </p>
 */
@AutoConfiguration
@EnableFeignClients(basePackages = "com.agentdoc.common.feign")
public class FeignAutoConfiguration {

    /**
     * 用户令牌透传拦截器（默认装配，可覆盖）。
     * @return 拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthHeaderForwardInterceptor.class)
    public AuthHeaderForwardInterceptor authHeaderForwardInterceptor() {
        return new AuthHeaderForwardInterceptor();
    }
}
