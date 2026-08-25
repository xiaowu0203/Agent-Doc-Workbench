package com.agentdoc.auth.config;

import com.agentdoc.auth.service.JwtService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 安全配置类
 * 基于OAuth2 ResourceServer + JWT无状态认证，不使用session会话
 * 统一鉴权规则、跨域、JWT解析、401未授权JSON返回、密码加密
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    // JSON序列化工具，用于输出自定义统一失败Result响应
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * 密码加密器Bean
     * 使用BCrypt算法做密码哈希，注册/登录时密码加密校验统一使用该实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT解码器
     * 使用非对称公钥解析JWT令牌，校验token签名合法性
     * 公钥由JwtService提供
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build();
    }

    /**
     * Spring Security核心安全过滤链
     * 配置：关闭CSRF、跨域、无状态会话、接口放行规则、oauth2资源服务、异常鉴权入口
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭CSRF防护，JWT前后端分离无cookie，不需要CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // 启用自定义跨域配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 设置会话策略：无状态，不创建、不使用HttpSession，完全依靠JWT令牌认证
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 请求鉴权规则配置
                .authorizeHttpRequests(auth -> auth
                        // 无需认证放行接口：登录注册、刷新令牌、登出、健康检查、Swagger文档、jwks公钥接口
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/oauth2/jwks",
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // 放行所有OPTIONS预检请求，解决前端跨域OPTIONS401问题
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 其余全部接口必须经过身份认证
                        .anyRequest().authenticated())
                // OAuth2 资源服务器配置，使用JWT模式解析token
                .oauth2ResourceServer(rs -> rs
                        // 指定JWT解码器，使用公钥验签
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                        // JWT校验失败（token无效、过期、篡改）自定义401入口，返回业务JSON，不返回默认html
                        .authenticationEntryPoint(json401EntryPoint()))
                // 全局Security鉴权异常处理，未登录、权限不足等场景，复用同一个401 JSON入口
                .exceptionHandling(eh -> eh.authenticationEntryPoint(json401EntryPoint()));
        return http.build();
    }

    /**
     * 统一未授权401响应处理器
     * 两种场景会进入此处理器：
     * 1. oauth2ResourceServer JWT校验失败：token过期、签名错误、token为空
     * 2. 全局security异常：未携带凭证访问受保护接口
     * 返回统一业务Result JSON格式，UTF‑8编码，状态码401
     */
    private AuthenticationEntryPoint json401EntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 输出统一失败返回体，错误码：UNAUTHORIZED
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(ErrorCode.UNAUTHORIZED)));
        };
    }

    /**
     * 全局CORS跨域配置源
     * 允许全部来源、全部请求方法、全部请求头，支持凭证（cookie/auth header）
     * 适配前后端分离前端项目跨域访问后端接口
     * setAllowedOriginPatterns("*")配合allowCredentials=true，比setAllowedOrigins更灵活
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有来源域
        config.setAllowedOriginPatterns(List.of("*"));
        // 允许全部HTTP请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // 允许全部请求头
        config.setAllowedHeaders(List.of("*"));
        // 允许携带凭证（Authorization token、cookie）
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对全部接口应用这套跨域规则
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
