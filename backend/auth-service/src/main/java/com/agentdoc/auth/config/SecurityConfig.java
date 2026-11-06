package com.agentdoc.auth.config;

import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.security.OidcHumanAuthenticationConverter;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.config.TaskInternalAuthProperties;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.filter.TaskInternalAuthenticationFilter;
import com.agentdoc.common.utils.TaskRecoveryJwtUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URI;
import java.util.List;

/**
 * Spring Security 安全配置类
 * 基于OAuth2 ResourceServer + JWT无状态认证，不使用session会话
 * 统一鉴权规则、跨域、JWT解析、401未授权JSON返回、密码加密
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(TaskInternalAuthProperties.class)
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
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
    public JwtDecoder jwtDecoder() {
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build();
        return token -> OnlineCapabilityUtils.rejectGeneralAccess(TaskRecoveryJwtUtils.rejectGeneralAccess(decoder.decode(token)));
    }

    /** OIDC模式必须显式配置可信issuer，启动时读取发现文档并严格验证issuer。 */
    @Bean
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "oidc")
    public JwtDecoder oidcJwtDecoder(IdentityBindingProperties properties, OpaqueTokenIntrospector introspector) {
        if (properties.getIssuer() == null || properties.getIssuer().isBlank()) {
            throw new IllegalStateException("OIDC模式必须配置可信issuer");
        }
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(properties.getIssuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(JwtConstant.OIDC_CLOCK_SKEW_SECONDS)),
                new JwtIssuerValidator(properties.getIssuer())));
        return token -> {
            var jwt = decoder.decode(token);
            try {
                introspector.introspect(token);
            } catch (BadOpaqueTokenException error) {
                throw new BadJwtException("令牌已失效");
            } catch (OAuth2IntrospectionException error) {
                throw new JwtException("身份状态服务不可用");
            }
            return jwt;
        };
    }

    /** 每次人类请求在线确认IdP状态；网络异常不可降级为离线放行。 */
    @Bean
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "oidc")
    public OpaqueTokenIntrospector oidcTokenIntrospector(IdentityBindingProperties properties) {
        if (properties.getIntrospectionUri() == null || properties.getIntrospectionUri().isBlank()
                || properties.getIntrospectionClientId() == null || properties.getIntrospectionClientId().isBlank()
                || properties.getIntrospectionClientSecret() == null || properties.getIntrospectionClientSecret().isBlank()) {
            throw new IllegalStateException("OIDC模式必须配置受众客户端的introspection端点和凭据");
        }
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        var rest = new RestTemplate(requestFactory);
        rest.getInterceptors().add(new BasicAuthenticationInterceptor(
                properties.getIntrospectionClientId(), properties.getIntrospectionClientSecret()));
        var introspector = new SpringOpaqueTokenIntrospector(properties.getIntrospectionUri(), rest);
        introspector.setRequestEntityConverter(token -> {
            var form = new RedactedTokenForm();
            form.add("token", token);
            return RequestEntity.post(URI.create(properties.getIntrospectionUri()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).body(form);
        });
        return introspector;
    }

    /** 防止HTTP客户端在调试级别格式化表单时输出Bearer。 */
    private static final class RedactedTokenForm extends LinkedMultiValueMap<String, String> {
        @Override public String toString() { return "[REDACTED]"; }
    }

    /** 专用线上凭证使用本机公钥，不通过禁止窄令牌的普通解码器。 */
    @Bean
    public OnlineCapabilityVerifier onlineCapabilityVerifier(SecurityVerifyProperties properties) {
        return new OnlineCapabilityVerifier(NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build(), properties);
    }

    /**
     * Spring Security核心安全过滤链
     * 配置：关闭CSRF、跨域、无状态会话、接口放行规则、oauth2资源服务、异常鉴权入口
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TaskInternalAuthenticationFilter internalFilter,
                                                   JwtDecoder decoder,
                                                   OidcHumanAuthenticationConverter oidcConverter,
                                                   Environment environment) throws Exception {
        boolean oidc = "oidc".equalsIgnoreCase(environment.getProperty("workbench.auth.mode", "local"));
        http.addFilterBefore(internalFilter, AnonymousAuthenticationFilter.class);
        http
                // 关闭CSRF防护，JWT前后端分离无cookie，不需要CSRF
                .csrf(AbstractHttpConfigurer::disable)
                // 启用自定义跨域配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 设置会话策略：无状态，不创建、不使用HttpSession，完全依靠JWT令牌认证
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 请求鉴权规则配置
                .authorizeHttpRequests(auth -> {
                    if (oidc) {
                        auth.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").denyAll();
                    }
                    auth
                        // 无需认证放行接口：登录注册、刷新令牌、登出、健康检查、Swagger文档、jwks公钥接口
                        .requestMatchers(
                                "/api/auth/internal/task-recovery-capabilities",
                                "/api/auth/internal/task-draft-finalization-capabilities",
                                "/api/auth/internal/online-capabilities",
                                "/api/auth/internal/online-release",
                                "/api/auth/internal/online-waits/*/exchange", "/api/auth/internal/online-waits/*/renew",
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
                        .anyRequest().authenticated();
                })
                // OAuth2 资源服务器配置，使用JWT模式解析token
                .oauth2ResourceServer(rs -> {
                    if (oidc) {
                        rs.withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                var handler = new AuthenticationEntryPointFailureHandler(jsonOidcEntryPoint());
                                handler.setRethrowAuthenticationServiceException(false);
                                filter.setAuthenticationFailureHandler(handler);
                                return filter;
                            }
                        });
                    }
                    rs
                        // 指定JWT解码器，使用公钥验签
                        .jwt(jwt -> {
                            jwt.decoder(decoder);
                            if (oidc) { jwt.jwtAuthenticationConverter(oidcConverter); }
                        })
                        // JWT校验失败（token无效、过期、篡改）自定义401入口，返回业务JSON，不返回默认html
                        .authenticationEntryPoint(oidc ? jsonOidcEntryPoint() : json401EntryPoint());
                })
                // 全局Security鉴权异常处理，未登录、权限不足等场景，复用同一个401 JSON入口
                .exceptionHandling(eh -> eh.authenticationEntryPoint(oidc ? jsonOidcEntryPoint() : json401EntryPoint()));
        return http.build();
    }

    /** 仅保护Task/Worker签发，不将机器认证扩散到用户API。 */
    @Bean
    public TaskInternalAuthenticationFilter taskInternalAuthenticationFilter(TaskInternalAuthProperties properties) {
        return new TaskInternalAuthenticationFilter(properties, TaskInternalAuthConstant.SIGNING_PATHS);
    }

    /** 只在Spring Security链内执行，避免Servlet容器提前注册造成上下文错位。 */
    @Bean
    public FilterRegistrationBean<TaskInternalAuthenticationFilter> taskInternalFilterRegistration(
            TaskInternalAuthenticationFilter filter) {
        FilterRegistrationBean<TaskInternalAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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

    private AuthenticationEntryPoint jsonOidcEntryPoint() {
        return (request, response, error) -> {
            if (error instanceof AuthenticationServiceException) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.SERVICE_UNAVAILABLE)));
            } else {
                json401EntryPoint().commence(request, response, error);
            }
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
