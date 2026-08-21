package com.agentdoc.common.config;

import com.agentdoc.common.controller.PingController;
import com.agentdoc.common.handler.GlobalExceptionHandler;
import com.agentdoc.common.security.PermissionInterceptor;
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
 * common‑web 模块自动装配类，Servlet Web环境生效。
 * <p>
 * 自动注册组件：
 * <ul>
 * <li>{@link GlobalExceptionHandler}：全局异常处理器</li>
 * <li>{@link TraceIdFilter}：TraceId链路过滤器</li>
 * <li>{@link UserContextFilter}：登录用户上下文过滤器</li>
 * <li>{@link PingController}：服务探活ping接口，可配置开关</li>
 * <li>{@link PermissionInterceptor}：鉴权拦截器，仅拦截 /api/** 路径</li>
 * </ul>
 * <p>条件：仅 Servlet(SpringMVC) 环境加载；WebFlux环境不会实例化本配置。
 * </p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebAutoConfiguration implements WebMvcConfigurer {

    /** 当前应用服务名称，取自 spring.application.name */
    @Value("${spring.application.name:unknown}")
    private String serviceName;

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
     * 用户上下文过滤器，容器不存在时才创建。
     * 从请求头解析登录主体信息，填充 {UserContext}，请求结束清理上下文。
     * @return UserContextFilter 实例
     */
    @Bean
    @ConditionalOnMissingBean(UserContextFilter.class)
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    /**
     * 探活Ping控制器，默认开启，可通过配置 agent‑doc.web.ping‑enabled=false 关闭。
     * @return PingController 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "agent-doc.web", name = "ping-enabled", havingValue = "true", matchIfMissing = true)
    public PingController pingController() {
        return new PingController(serviceName);
    }

    /**
     * 注册鉴权拦截器 {@link PermissionInterceptor}。
     * <p>仅拦截匹配 /api/** 的接口；处理 {RequireLogin}、{RequirePermission} 注解鉴权。</p>
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PermissionInterceptor()).addPathPatterns("/api/**");
    }
}
