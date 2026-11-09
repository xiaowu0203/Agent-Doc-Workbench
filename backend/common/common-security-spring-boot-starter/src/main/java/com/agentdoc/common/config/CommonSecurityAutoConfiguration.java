package com.agentdoc.common.config;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.security.OidcRemoteHumanAuthenticationConverter;
import com.agentdoc.common.security.OidcBearerTokenResolver;
import com.agentdoc.common.security.TaskCapabilityAuthenticationFilter;
import com.agentdoc.common.filter.TaskInternalAuthenticationFilter;
import com.agentdoc.common.utils.TaskRecoveryJwtUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 通用Spring Security自动装配配置
 * <p>
 * 仅在Servlet Web环境生效；提供无状态OAuth2 Resource‑Server基础能力。
 * 功能：
 * 1. 当配置 agent‑doc.security.jwks‑url 存在时，自动创建基于JWKS的JwtDecoder；
 * 2. 构建默认SecurityFilterChain：关闭CSRF，设置无状态会话；默认全部接口放行；
 * 3. 如果存在JwtDecoder，则开启OAuth2资源服务JWT解析能力；
 * 4. 自定义401未授权返回JSON统一格式Result.fail，而非默认Spring html页面；
 * </p>
 * 条件说明：
 * - {@code @ConditionalOnMissingBean(SecurityFilterChain.class)}：业务服务可以自定义SecurityFilterChain覆盖此默认配置
 */
@AutoConfiguration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingBean(SecurityFilterChain.class)
@EnableConfigurationProperties(SecurityVerifyProperties.class)
public class CommonSecurityAutoConfiguration {

    /** JSON序列化器，用于输出自定义JSON格式401响应 */
    private final ObjectMapper objectMapper;

    public CommonSecurityAutoConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构建JWT解码器 Bean
     * <p>
     * 条件：配置项 agent‑doc.security.jwks‑url 已配置，且容器不存在自定义JwtDecoder
     * 基于JWKS地址远程拉取公钥集合，自动完成JWT验签、过期时间(exp/nbf)校验。
     * </p>
     * @param properties 安全配置属性，包含jwks‑url地址
     * @return NimbusJwtDecoder JWT解码器实例
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "jwks-url")
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
    public JwtDecoder businessJwtDecoder(SecurityVerifyProperties properties) {
        JwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwksUrl()).build();
        return token -> OnlineCapabilityUtils.rejectGeneralAccess(TaskRecoveryJwtUtils.rejectGeneralAccess(decoder.decode(token)));
    }

    /** OIDC人类令牌使用IdP密钥；业务Capability仍由独立验证器使用Auth密钥。 */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "oidc")
    public JwtDecoder oidcBusinessJwtDecoder(Environment environment) {
        String issuer = environment.getRequiredProperty("workbench.auth.oidc.issuer");
        if (issuer.isBlank()) { throw new IllegalStateException("OIDC模式必须配置可信issuer"); }
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(JwtConstant.OIDC_CLOCK_SKEW_SECONDS)), new JwtIssuerValidator(issuer)));
        return decoder;
    }

    /**
     * 默认安全过滤链配置
     * <p>
     * 1. 关闭CSRF防护；设置会话策略为无状态，不创建HttpSession；
     * 2. 默认放行全部请求 {@code anyRequest().permitAll()}，鉴权交给业务注解/拦截器处理；
     * 3. 如果容器中存在JwtDecoder，则开启oauth2资源服务，自动解析Authorization: Bearer JWT；
     * 4. JWT校验失败时使用自定义json401EntryPoint返回统一JSON错误报文。
     * </p>
     * @param http HttpSecurity构建器
     * @param decoderProvider ObjectProvider延迟获取JwtDecoder，处理Bean可能不存在的场景
     * @param internalFilterProvider 业务模块显式提供的内部认证过滤器；没有时保持原链行为
     * @return SecurityFilterChain 安全过滤链
     * @throws Exception http配置构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<JwtDecoder> decoderProvider,
                                                   ObjectProvider<TaskInternalAuthenticationFilter> internalFilterProvider,
                                                   ObjectProvider<AuthIdentityFeign> identityProvider,
                                                   ObjectProvider<BearerTokenResolver> bearerResolverProvider,
                                                   ObjectProvider<TaskCapabilityAuthenticationFilter> capabilityFilterProvider,
                                                   SecurityVerifyProperties securityProperties,
                                                   Environment environment) throws Exception {
        String mode = environment.getProperty("workbench.auth.mode", "local");
        if (!"local".equalsIgnoreCase(mode) && !"oidc".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("未知身份认证模式");
        }
        boolean oidc = "oidc".equalsIgnoreCase(mode);
        // 获取JwtDecoder，可能为null（未配置jwks‑url时）
        JwtDecoder jwtDecoder = decoderProvider.getIfAvailable();
        AuthIdentityFeign identity = oidc ? identityProvider.getIfAvailable() : null;
        if (oidc && (jwtDecoder == null || identity == null)) {
            throw new IllegalStateException("OIDC模式缺少JWT解码器或本人身份客户端");
        }
        http
                // 关闭CSRF，前后端分离无状态API服务一般关闭
                .csrf(AbstractHttpConfigurer::disable)
                // 设置无状态，不使用session，完全依靠token鉴权
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 默认全部接口放行；业务层使用注解、自定义Filter、AOP做业务鉴权控制
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        // 如果JwtDecoder可用，则开启OAuth2 Resource Server JWT解析能力
        if (jwtDecoder != null) {
            http.oauth2ResourceServer(rs -> {
                if (oidc) {
                    BearerTokenResolver delegate = bearerResolverProvider.getIfAvailable(DefaultBearerTokenResolver::new);
                    rs.bearerTokenResolver(new OidcBearerTokenResolver(delegate, securityProperties,
                            capabilityFilterProvider.getIfAvailable() != null));
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
                rs.jwt(jwt -> {
                    jwt.decoder(jwtDecoder);
                    if (oidc) { jwt.jwtAuthenticationConverter(new OidcRemoteHumanAuthenticationConverter(identity)); }
                })
                    // token解析失败、未携带token时使用自定义JSON格式401返回
                    .authenticationEntryPoint(oidc ? jsonOidcEntryPoint() : json401EntryPoint());
            });
        }
        TaskInternalAuthenticationFilter internalFilter = internalFilterProvider.getIfAvailable();
        if (internalFilter != null) {
            http.addFilterBefore(internalFilter, AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * 自定义401未授权异常处理器
     * <p>
     * Spring Security默认返回HTML页面；此处输出项目统一JSON返回体 Result.fail(UNAUTHORIZED)。
     * 触发场景：Bearer JWT解析失败、签名错误、过期、缺失token等鉴权异常。
     * </p>
     * @return AuthenticationEntryPoint 鉴权入口点
     */
    private AuthenticationEntryPoint json401EntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.UNAUTHORIZED)));
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
}
