package com.agentdoc.common.config;

import com.agentdoc.common.handler.GlobalExceptionHandler;
import com.agentdoc.common.security.PermissionInterceptor;
import com.agentdoc.common.web.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.MalformedURLException;

/**
 * common‑web 模块自动装配类，Servlet Web环境生效。
 * <p>
 * 自动注册组件（均为横切基础设施，不含业务形态代码；服务探活由各服务 Actuator health 承担）：
 * <ul>
 * <li>{@link GlobalExceptionHandler}：全局异常处理器</li>
 * <li>{@link TraceIdFilter}：TraceId链路过滤器</li>
 * <li>{@link PermissionInterceptor}：鉴权拦截器，仅拦截 /api/** 路径</li>
 * </ul>
 * 安全（JWT 解析与 401）由 {@link CommonSecurityAutoConfiguration} 承担（Spring Security Resource Server）。
 * <p>条件：仅 Servlet(SpringMVC) 环境加载；WebFlux环境不会实例化本配置。
 * </p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityVerifyProperties.class)
public class CommonWebAutoConfiguration implements WebMvcConfigurer {

    /**
     * 全局异常处理器，容器不存在时才创建。
     * @return GlobalExceptionHandler 实例
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * TraceId链路过滤器，容器不存在时才创建。
     * 负责解析/生成traceId，设置到 {TraceContext}，请求结束清理上下文。
     * @return TraceIdFilter 实例
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /**
     * JWT 解码器：配置 {@code agent-doc.security.jwks-url} 后启用（auth-service不用自认证），
     * 从 auth-service 的 JWK Set 拉取并缓存 RSA 公钥，供 Spring Security Resource Server 验签解析。
     * 未配置 jwks-url 的服务（如 auth-service 用自己的 SecurityConfig）不创建，避免与自定义解码器冲突。
     * @param properties 安全校验配置
     * @return JwtDecoder 实例
     * @throws MalformedURLException jwks-url 非法时抛出
     */
    @Bean
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "jwks-url")
    public JwtDecoder businessJwtDecoder(SecurityVerifyProperties properties) throws MalformedURLException {
        return NimbusJwtDecoder.withJwkSetUri(properties.getJwksUrl()).build();
    }

    /**
     * 注册鉴权拦截器 {@link PermissionInterceptor}。
     * <p>仅拦截匹配 /api/** 的接口；处理 {RequireLogin} 注解登录鉴权。</p>
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionInterceptor()).addPathPatterns("/api/**");
    }
}
