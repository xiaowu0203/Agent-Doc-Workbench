package com.agentdoc.common.config;

import com.agentdoc.common.security.PermissionInterceptor;
import com.agentdoc.common.web.GlobalExceptionHandler;
import com.agentdoc.common.web.PingController;
import com.agentdoc.common.web.TraceIdFilter;
import com.agentdoc.common.web.UserContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Servlet Web 服务通用自动装配（common-web-spring-boot-starter）：
 * <ul>
 *     <li>全局异常处理器：统一转换为 Result 结构（可覆盖）</li>
 *     <li>TraceId 链路过滤器：生成/复用 X-Trace-Id，写入响应头与 MDC</li>
 *     <li>用户上下文过滤器：接收网关透传的 X-User-* 头填充 UserContext</li>
 *     <li>权限拦截器：校验 @RequireLogin / @RequirePermission 注解接口</li>
 *     <li>健康探测 Ping 接口：默认开启，agent-doc.web.ping-enabled=false 关闭</li>
 * </ul>
 * 仅 Servlet Web 应用生效（WebFlux gateway 基于条件注解自动跳过）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration implements WebMvcConfigurer {

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean(UserContextFilter.class)
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent-doc.web", name = "ping-enabled", havingValue = "true", matchIfMissing = true)
    public PingController pingController() {
        return new PingController(serviceName);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionInterceptor()).addPathPatterns("/api/**");
    }
}
