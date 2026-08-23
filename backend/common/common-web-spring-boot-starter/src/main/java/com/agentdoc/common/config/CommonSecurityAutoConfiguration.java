package com.agentdoc.common.config;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import java.nio.charset.StandardCharsets;

/**
 * 业务服务统一安全自动装配
 * <p>
 * 注解驱动安全模型：Spring Security层面放行全部请求，登录鉴权交给业务注解 {@code @RequireLogin} + {@code PermissionInterceptor} 完成；
 * 当存在可用 {@link JwtDecoder} 实例时，开启OAuth2资源服务器，自动解析 Authorization Bearer JWT，填充SecurityContext。
 * </p>
 * <p>生效条件：
 * <ul>
 *     <li>Servlet Web环境</li>
 *     <li>当前上下文不存在自定义 {@link SecurityFilterChain} Bean，避免与业务服务自身SecurityConfig冲突；
 *         例如 auth‑service 自身已有安全过滤链，则本自动装配直接跳过不生效</li>
 * </ul>
 * </p>
 * <p>规则说明：
 * <ul>
 * <li>{@code anyRequest().permitAll()}：Security框架不做接口拦截，所有请求放行；登录校验由业务层{@code @RequireLogin}注解拦截器实现，公共接口无需额外配置即可匿名访问</li>
 * <li>{@code oauth2ResourceServer}：当JwtDecoder可用时激活，自动解析{@code Authorization: Bearer <JWT>}，验签后写入SecurityContext；
 *     token无效、过期、签名错误直接返回401；JwtDecoder由配置项{@code agent-doc.security.jwks-url}驱动生成</li>
 * <li>统一401异常输出：返回项目标准 {@link Result} JSON结构，保证和网关401返回格式语义一致</li>
 * </ul>
 * </p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingBean(SecurityFilterChain.class)
public class CommonSecurityAutoConfiguration {

    /**
     * JSON序列化实例，用于输出统一401响应报文
     */
    private final ObjectMapper objectMapper;

    public CommonSecurityAutoConfiguration(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }

    /**
     * 构建业务服务默认安全过滤链
     * <p>
     * 双重模式降级逻辑：
     * <ol>
     * <li>存在JwtDecoder（配置了jwks‑url）：开启oauth2ResourceServer，自动完成JWT验签解析；</li>
     * <li>缺失JwtDecoder（未配置jwks‑url）：关闭JWT解析，仅保留permitAll全部放行，登录鉴权完全交给{@code @RequireLogin}业务拦截器，用于本地开发调试场景。</li>
     * </ol>
     * @param http Spring安全构建器
     * @param decoderProvider JwtDecoder提供者，通过ObjectProvider做可选依赖，Bean不存在不会报错
     * @return SecurityFilterChain 安全过滤链Bean
     * @throws Exception http构建过程抛出异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<JwtDecoder> decoderProvider) throws Exception {
        // 获取容器中可用的JwtDecoder，不存在返回null
        JwtDecoder jwtDecoder = decoderProvider.getIfAvailable();
        http
                // 关闭 CSRF：JWT 无状态前后端分离
                .csrf(AbstractHttpConfigurer::disable)
                // 设置无状态会话策略，不创建、不使用HttpSession，完全依赖JWT令牌认证
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Security层面放行全部请求，接口鉴权下沉到业务@RequireLogin拦截器
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        // 存在JwtDecoder，开启OAuth2资源服务器JWT自动验签解析
        if (jwtDecoder != null) {
            // OAuth2 Resource Server：解析 Authorization Bearer JWT 进 SecurityContext；无效 token 401
            http.oauth2ResourceServer(rs -> rs
                    .jwt(jwt -> jwt.decoder(jwtDecoder))
                    // JWT校验失败时使用自定义401入口，输出统一业务JSON
                    .authenticationEntryPoint(json401EntryPoint()));
        }
        return http.build();
    }

    /**
     * 统一401未授权响应处理器
     * <p>
     * 触发场景：JWT签名错误、token过期、token格式非法。
     * 返回项目标准Result失败JSON，设置UTF‑8编码，避免中文乱码。
     * </p>
     *
     * @return AuthenticationEntryPoint 认证失败入口实现
     */
    private AuthenticationEntryPoint json401EntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.UNAUTHORIZED)));
        };
    }
}
