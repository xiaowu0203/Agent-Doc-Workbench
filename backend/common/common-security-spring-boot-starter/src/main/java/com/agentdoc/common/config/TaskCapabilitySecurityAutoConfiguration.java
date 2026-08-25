package com.agentdoc.common.config;

import com.agentdoc.common.security.TaskCapabilityAuthenticationFilter;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Agent任务能力令牌自动装配配置
 * <p>
 * 依赖基础资源服务安全组件，在{@link CommonSecurityAutoConfiguration}之后执行；
 * 负责装配Agent任务令牌相关Bean：验签器、请求过滤器。
 * <ol>
 * <li>需要容器中已经存在{@link JwtDecoder}，依赖jwks‑url配置，复用auth‑service公钥完成JWT验签；</li>
 * <li>{@link TaskCapabilityVerifier}：JWT验签 + Agent业务声明校验；</li>
 * <li>{@link TaskCapabilityAuthenticationFilter}：仅配置 {@code task‑capability‑filter‑enabled=true} 才创建，
 * 用于解析HTTP请求头 {@link com.agentdoc.common.constant.HeaderConstants#X_TASK_CAPABILITY}，临时注入Agent身份到Security上下文；</li>
 * </ol>
 * </p>
 * 注意：
 * <ul>
 * <li>仅Servlet(MVC) Web环境生效，WebFlux/Gateway不会加载；</li>
 * <li>Agent令牌能力默认关闭，业务服务需要显式开启配置才会注册Filter；</li>
 * <li>本组件属于资源服务侧能力，只做令牌校验，不负责签发令牌，令牌由auth‑service授权服务器签发。</li>
 * </ul>
 */
@AutoConfiguration(after = CommonSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TaskCapabilitySecurityAutoConfiguration {

    /**
     * 构建Agent任务令牌验签器
     * <p>
     * 生效条件：
     * <ul>
     * <li>容器中已经存在JwtDecoder Bean（由CommonSecurityAutoConfiguration或者业务自定义提供）；</li>
     * <li>配置了agent‑doc.security.jwks‑url公钥地址；</li>
     * <li>容器不存在同名Bean，允许业务自定义覆盖。</li>
     * </ul>
     * 复用全局JwtDecoder完成JWT签名、过期校验；额外校验agent专属claim（actor_type、scope）业务语义。
     * </p>
     * @param decoder JWT解码器，从JWKS拉取公钥
     * @return TaskCapabilityVerifier Agent令牌验签实例
     */
    @Bean
    @ConditionalOnBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "jwks-url")
    @ConditionalOnMissingBean
    public TaskCapabilityVerifier taskCapabilityVerifier(JwtDecoder decoder) {
        return new TaskCapabilityVerifier(decoder);
    }

    /**
     * 构建Agent任务令牌请求过滤器
     * <p>
     * 生效条件：
     * <ul>
     * <li>已经存在TaskCapabilityVerifier验签器实例；</li>
     * <li>配置 {@code agent‑doc.security.task‑capability‑filter‑enabled=true} 显式开启该能力；</li>
     * <li>容器不存在同名Bean，允许业务自定义覆盖。</li>
     * </ul>
     * 过滤器职责：解析请求头X‑TASK‑CAPABILITY或配置端点的Bearer令牌，验签通过后临时设置Agent身份Security上下文，
     * 同时写入{@link com.agentdoc.common.context.TaskCapabilityContext}供Feign拦截器向下游透传令牌；
     * 使用「保存‑恢复原始SecurityContext」模式，避免线程池ThreadLocal身份泄露。
     * </p>
     * @param verifier Agent令牌验签器
     * @return TaskCapabilityAuthenticationFilter Agent令牌解析过滤器
     */
    @Bean
    @ConditionalOnBean(TaskCapabilityVerifier.class)
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "task-capability-filter-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean
    public TaskCapabilityAuthenticationFilter taskCapabilityAuthenticationFilter(
            TaskCapabilityVerifier verifier, SecurityVerifyProperties properties) {
        return new TaskCapabilityAuthenticationFilter(verifier, properties);
    }
}
