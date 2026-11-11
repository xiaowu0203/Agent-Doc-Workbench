package com.agentdoc.common.config;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.security.RemoteHumanAuthenticationConverter;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.utils.LocalIdentityTokenUtils;
import com.agentdoc.common.security.OidcBearerTokenResolver;
import com.agentdoc.common.security.TaskCapabilityAuthenticationFilter;
import com.agentdoc.common.filter.TaskInternalAuthenticationFilter;
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
import org.springframework.boot.ApplicationRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
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
import java.time.Instant;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.util.Locale;

/**
 * 通用Spring Security自动装配配置
 *
 * <p>
 * 仅在Servlet Web环境生效；提供无状态OAuth2 Resource-Server基础能力。
 * 功能：
 * 1. local模式仅使用identity-jwks-url创建人类JwtDecoder，缺失配置拒绝启动；
 * 2. 当 workbench.auth.mode=oidc 时，基于IdP issuer自动创建OIDC JwtDecoder；
 * 3. 构建默认SecurityFilterChain：关闭CSRF，设置无状态会话；默认全部接口放行；
 * 4. 两模式均开启人类JWT解析及Auth本人身份复核；
 * 5. 自定义401未授权返回JSON统一格式Result.fail，而非默认Spring HTML页面；
 * 6. 业务模块若显式提供 TaskInternalAuthenticationFilter，则把内部验签Filter挂进安全链。
 * </p>
 *
 * 条件说明：
 * - {@code @ConditionalOnMissingBean(SecurityFilterChain.class)}：类级条件，
 *   业务服务只要自定义了任意 SecurityFilterChain Bean，本自动配置类整体不装配，
 *   避免与业务自定义链冲突（注意是"整类跳过"，不是只跳过某一个Bean）。
 */
@AutoConfiguration
@Slf4j
// Spring Boot 3 自动配置注解，等价于 @Configuration(proxyBeanMethods=false) + 注册自动装配
@EnableMethodSecurity
// 开启方法级鉴权，使 @PreAuthorize / @PostAuthorize 等注解生效（默认 prePostEnabled=true）
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// 仅Servlet Web应用装配；WebFlux响应式环境下Security模型不同，必须排除
@ConditionalOnMissingBean(SecurityFilterChain.class)
// 容器中不存在自定义SecurityFilterChain时才生效，给业务方留覆盖入口
@EnableConfigurationProperties({SecurityVerifyProperties.class, WorkbenchAuthProperties.class})
// 绑定 agent-doc.security.* 配置（至少含 jwks-url），注册为Bean供后续注入
public class CommonSecurityAutoConfiguration {

    /**
     * JSON序列化器，用于输出自定义JSON格式401/503响应
     */
    private final ObjectMapper objectMapper;

    public CommonSecurityAutoConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构建JWT解码器 Bean（local模式）
     *
     * <p>
     * 仅local模式；使用独立身份JWKS验签，限制算法、用途与时间。
     * </p>
     *
     * <p>
     * 旧用户JWT、SERVICE及Task/Worker/恢复/线上凭据均不能通过人类契约。
     * </p>
     *
     * @param properties 包含identity-jwks-url，与业务Capability的jwks-url分开
     * @return JwtDecoder JWT解码器实例
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    // 已有自定义JwtDecoder时不覆盖
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
    // 仅local模式装配；未显式配置mode时按local处理
    public JwtDecoder businessJwtDecoder(SecurityVerifyProperties properties) {
        URI endpoint;
        try {
            endpoint = URI.create(properties.getIdentityJwksUrl());
            if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null || !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalStateException("local模式必须配置无凭据/查询的HTTP(S)身份JWKS地址");
        }
        var requests = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method); connection.setInstanceFollowRedirects(false);
            }
        };
        requests.setConnectTimeout(Duration.ofSeconds(2)); requests.setReadTimeout(Duration.ofSeconds(5));
        var decoder = NimbusJwtDecoder.withJwkSetUri(endpoint.toString()).jwsAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false).restOperations(new RestTemplate(requests)).build();
        decoder.setJwtValidator(jwt -> {
            try {
                LocalIdentityTokenUtils.requireHuman(jwt, Instant.now());
                return OAuth2TokenValidatorResult.success();
            } catch (JwtException | IllegalArgumentException error) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "无效的本地人类凭据", null));
            }
        });
        return decoder;
    }

    @Bean
    ApplicationRunner logAuthenticationMode(WorkbenchAuthProperties properties) {
        return arguments -> log.info("认证模式={}", properties.getMode().name().toLowerCase(Locale.ROOT));
    }

    /** OIDC人类令牌使用IdP密钥；业务Capability仍由独立验证器使用Auth密钥。 */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    // 与local分支互斥：同一个容器内只会生效一个
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "oidc")
    // 仅oidc模式装配
    public JwtDecoder oidcBusinessJwtDecoder(Environment environment) {
        // 从配置读取可信issuer；OIDC模式下issuer是身份信任链的根，必须显式配置
        String issuer = environment.getRequiredProperty("workbench.auth.oidc.issuer");
        // issuer为空直接fail-fast，避免用空issuer去起OIDC discovery（会拉取到不可信元数据）
        if (issuer.isBlank()) { throw new IllegalStateException("OIDC模式必须配置可信issuer"); }
        // 通过issuer discovery自动获取 jwks_uri、支持的签名算法等元数据并构建解码器
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
        // 显式配置校验器：时间戳校验（带OIDC允许的时钟偏移）+ issuer精确匹配校验
        // 不依赖默认校验器的隐式行为，防止issuer伪造、时钟漂移误判
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(JwtConstant.OIDC_CLOCK_SKEW_SECONDS)), new JwtIssuerValidator(issuer)));
        // 注意：OIDC分支不加通用/恢复令牌拒绝逻辑，因为这是人类令牌专用解码器，
        // 业务Capability令牌由独立的验证器使用Auth密钥校验，两条通道不复用解码器
        return decoder;
    }

    /**
     * 默认安全过滤链配置
     *
     * <p>
     * 1. 关闭CSRF防护；设置会话策略为无状态，不创建HttpSession；
     * 2. 默认放行全部请求 {@code anyRequest().permitAll()}，鉴权交给业务注解/拦截器处理；
     * 3. 当前模式的JwtDecoder和本人客户端必须存在，自动解析人类Bearer；
     * 4. JWT校验失败时使用自定义JSON EntryPoint返回统一错误报文；
     * 5. 业务侧提供了 TaskInternalAuthenticationFilter 时，挂到匿名认证器之前。
     * </p>
     *
     * @param http HttpSecurity构建器
     * @param decoderProvider ObjectProvider延迟获取JwtDecoder，处理Bean可能不存在的场景
     * @param internalFilterProvider 业务模块显式提供的内部认证过滤器；没有时保持原链行为
     * @param identityProvider 两模式共用的受控本人Feign
     * @param bearerResolverProvider 可选的专用Bearer路由器
     * @param capabilityFilterProvider 决定任务Bearer是否交给独立验证器
     * @param securityProperties 安全配置属性
     * @param authenticationProperties 唯一部署认证模式
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
                                                   WorkbenchAuthProperties authenticationProperties) throws Exception {
        // 读取认证模式，未配置时默认local
        AuthenticationMode mode = authenticationProperties.getMode();
        // 模式白名单校验：只允许local/oidc，非法值直接fail-fast，避免拼错配置后静默降级
        if (mode == null) {
            throw new IllegalStateException("未知身份认证模式");
        }
        JwtDecoder jwtDecoder = decoderProvider.getIfAvailable();
        AuthIdentityFeign identity = identityProvider.getIfAvailable();
        if (jwtDecoder == null || identity == null) {
            throw new IllegalStateException("当前认证模式缺少JWT解码器或本人身份客户端");
        }
        http
                // 关闭CSRF：前后端分离、Token鉴权的无状态API，CSRF Token机制不适用且会拦截跨域请求
                .csrf(AbstractHttpConfigurer::disable)
                // 无状态会话：不创建也不读取HttpSession，身份完全靠Token承载
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 默认全部接口放行：授权判断下移到方法注解(@PreAuthorize)、自定义Filter、AOP，
                // 本链只负责"身份解析"，不负责"接口是否允许访问"
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        http.oauth2ResourceServer(rs -> {
            BearerTokenResolver delegate = bearerResolverProvider.getIfAvailable(DefaultBearerTokenResolver::new);
            rs.bearerTokenResolver(new OidcBearerTokenResolver(delegate, securityProperties,
                    capabilityFilterProvider.getIfAvailable() != null));
            rs.withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                @Override
                public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                    var handler = new AuthenticationEntryPointFailureHandler(jsonAuthenticationEntryPoint());
                    handler.setRethrowAuthenticationServiceException(false);
                    filter.setAuthenticationFailureHandler(handler);
                    return filter;
                }
            });
            rs.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(new RemoteHumanAuthenticationConverter(identity, mode)))
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint());
        });

        // 业务模块若显式提供了内部验签Filter，则挂进安全链
        TaskInternalAuthenticationFilter internalFilter = internalFilterProvider.getIfAvailable();
        if (internalFilter != null) {
            // 挂到AnonymousAuthenticationFilter之前：此时用户/Bearer身份已解析完毕，SecurityContext基本就绪，
            // 而匿名身份尚未兜底注入，内部验签结果可以正常参与后续授权判断
            // 注意：此处是无条件挂载，路径范围由Filter自身判定；
            // InternalAgentSecurityConfig 里 setEnabled(false) 只禁用了Servlet全局注册，不妨碍这里挂载
            http.addFilterBefore(internalFilter, AnonymousAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * 自定义401未授权异常处理器
     *
     * <p>
     * Spring Security默认返回HTML页面；此处输出项目统一JSON返回体 Result.fail(UNAUTHORIZED)。
     * 触发场景：Bearer JWT解析失败、签名错误、过期、缺失token等鉴权异常。
     * </p>
     *
     * @return AuthenticationEntryPoint 鉴权入口点
     */
    private AuthenticationEntryPoint json401EntryPoint() {
        return (request, response, ex) -> {
            // HTTP状态码固定401，与项目统一错误码语义对齐
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            // 输出统一响应体，前端/调用方按单一结构解析，不需要区分HTML与JSON
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.UNAUTHORIZED)));
        };
    }

    /**
     * 两模式共用的认证故障入口
     *
     * <p>
     * 语义区分：
     * - AuthenticationServiceException（身份服务本身不可用、远程调用失败）→ 503 SERVICE_UNAVAILABLE，
     *   表示"不是调用方凭证有问题，而是认证依赖不可用"，便于上游区分重试策略；
     * - 其余认证异常（凭证无效、过期、缺失）→ 回落到401统一处理。
     * </p>
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, error) -> {
            // 认证服务异常：属于上游依赖故障，返回503而不是401，避免把系统故障误报成凭证问题
            if (error instanceof AuthenticationServiceException) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.SERVICE_UNAVAILABLE)));
            } else {
                // 凭证类问题统一走401实现，保持两种模式的报文结构一致
                json401EntryPoint().commence(request, response, error);
            }
        };
    }
}
