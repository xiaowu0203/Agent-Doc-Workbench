package com.agentdoc.common.feign.config;

import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

/**
 * common-feign 自动装配：扫描 common-core 的 feign 契约包并注册 Feign 客户端。
 */
@AutoConfiguration
// 扫描指定包下 @FeignClient，自动注册feign客户端
@EnableFeignClients(basePackages = "com.agentdoc.common.feign")
// 绑定内部调用配置属性
@EnableConfigurationProperties(TaskInternalCallProperties.class)
public class FeignAutoConfiguration {

    /**
     * 用户令牌透传拦截器（默认装配，可覆盖）。
     * 没有自定义Bean时才创建，用于feign调用时透传鉴权头
     * @return 拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthHeaderForwardInterceptor.class)
    public AuthHeaderForwardInterceptor authHeaderForwardInterceptor(TaskInternalCallProperties properties, Environment environment) {
        return new AuthHeaderForwardInterceptor(properties,
                environment.getProperty("agent-doc.feign.identity-trusted-encrypted-network", Boolean.class, false));
    }
}
