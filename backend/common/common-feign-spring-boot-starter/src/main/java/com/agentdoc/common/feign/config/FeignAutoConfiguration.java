package com.agentdoc.common.feign.config;

import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.http.IdentitySelfFeignTransport;
import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.openfeign.FeignClientFactoryBean;
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

    @Bean(destroyMethod = "close")
    public IdentitySelfFeignTransport identitySelfFeignTransport(Environment environment) {
        if (environment.getProperty("spring.cloud.openfeign.lazy-attributes-resolution", Boolean.class, false)) {
            throw new IllegalStateException("本人身份Feign边界要求关闭lazy-attributes-resolution");
        }
        return new IdentitySelfFeignTransport();
    }

    /** 工厂的追加定制在普通配置之后执行，只锁定本人身份客户端的依赖边界。 */
    @Bean
    public static BeanPostProcessor identitySelfFeignBoundary(ObjectProvider<IdentitySelfFeignTransport> transports) {
        return new BeanPostProcessor() {
            @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof FeignClientFactoryBean factory && factory.getType() == AuthIdentityFeign.class) {
                    factory.addCustomizer(builder -> builder.client(transports.getObject().client())
                            .retryer(Retryer.NEVER_RETRY).logger(new Logger.NoOpLogger()).logLevel(Logger.Level.NONE)
                            .errorDecoder(new IdentitySelfFeignErrorDecoder())
                            .options(new Request.Options(AuthenticationHttpBudget.IDENTITY.connectTimeout(),
                                    AuthenticationHttpBudget.IDENTITY.readTimeout(), false)));
                }
                return bean;
            }
        };
    }

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
