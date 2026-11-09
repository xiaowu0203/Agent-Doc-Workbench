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
 *
 * <p>
 * 仅在Servlet Web环境生效；提供无状态OAuth2 Resource-Server基础能力。
 * 功能：
 * 1. 当配置 agent-doc.security.jwks-url 存在时（local模式），自动创建基于JWKS的JwtDecoder；
 * 2. 当 workbench.auth.mode=oidc 时，基于IdP issuer自动创建OIDC JwtDecoder；
 * 3. 构建默认SecurityFilterChain：关闭CSRF，设置无状态会话；默认全部接口放行；
 * 4. 存在JwtDecoder时开启OAuth2资源服务JWT解析能力；
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
// Spring Boot 3 自动配置注解，等价于 @Configuration(proxyBeanMethods=false) + 注册自动装配
@EnableMethodSecurity
// 开启方法级鉴权，使 @PreAuthorize / @PostAuthorize 等注解生效（默认 prePostEnabled=true）
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
// 仅Servlet Web应用装配；WebFlux响应式环境下Security模型不同，必须排除
@ConditionalOnMissingBean(SecurityFilterChain.class)
// 容器中不存在自定义SecurityFilterChain时才生效，给业务方留覆盖入口
@EnableConfigurationProperties(SecurityVerifyProperties.class)
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
     * 条件：容器无自定义JwtDecoder + 配置 agent-doc.security.jwks-url 存在
     * + workbench.auth.mode 为 local（matchIfMissing=true，即未配置时默认local）。
     * 基于JWKS地址远程拉取公钥集合，自动完成JWT验签、过期时间(exp/nbf)校验。
     * </p>
     *
     * <p>
     * 关键安全处理：解码后不直接返回，再串两道人造校验——
     * {@link TaskRecoveryJwtUtils#rejectGeneralAccess} 与 {@link OnlineCapabilityUtils#rejectGeneralAccess}，
     * 目的是拒绝"通用/恢复类令牌"走业务接口访问，防止不同用途的令牌被交叉复用（令牌混用防护）。
     * </p>
     *
     * @param properties 安全配置属性，包含jwks-url地址
     * @return JwtDecoder JWT解码器实例
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    // 已有自定义JwtDecoder时不覆盖
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "jwks-url")
    // 没有配置JWKS地址时不做任何事，避免启动即失败
    @ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
    // 仅local模式装配；未显式配置mode时按local处理
    public JwtDecoder businessJwtDecoder(SecurityVerifyProperties properties) {
        // 基于JWKS URI构建Nimbus解码器，公钥由startup后首次解析时远程拉取并缓存
        JwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwksUrl()).build();
        // 外层包一层Lambda JwtDecoder：先解码验签，再依次做恢复令牌、在线通用令牌的拒绝校验
        return token -> OnlineCapabilityUtils.rejectGeneralAccess(TaskRecoveryJwtUtils.rejectGeneralAccess(decoder.decode(token)));
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
     * 3. 容器中存在JwtDecoder时，开启oauth2资源服务，自动解析 Authorization: Bearer JWT；
     * 4. JWT校验失败时使用自定义JSON EntryPoint返回统一错误报文；
     * 5. 业务侧提供了 TaskInternalAuthenticationFilter 时，挂到匿名认证器之前。
     * </p>
     *
     * @param http HttpSecurity构建器
     * @param decoderProvider ObjectProvider延迟获取JwtDecoder，处理Bean可能不存在的场景
     * @param internalFilterProvider 业务模块显式提供的内部认证过滤器；没有时保持原链行为
     * @param identityProvider OIDC模式下远程查询本人身份的Feign客户端
     * @param bearerResolverProvider 可选的Bearer解析器（OIDC分支下被外层包装）
     * @param capabilityFilterProvider 任务能力鉴权Filter，决定OIDC Token解析策略的开关
     * @param securityProperties 安全配置属性
     * @param environment 环境配置，用于读取认证模式
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
        // 读取认证模式，未配置时默认local
        String mode = environment.getProperty("workbench.auth.mode", "local");
        // 模式白名单校验：只允许local/oidc，非法值直接fail-fast，避免拼错配置后静默降级
        if (!"local".equalsIgnoreCase(mode) && !"oidc".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("未知身份认证模式");
        }
        // 布尔化模式，后续多处分支复用
        boolean oidc = "oidc".equalsIgnoreCase(mode);
        // 获取JwtDecoder，可能为null（未配置jwks-url且非oidc模式时）
        JwtDecoder jwtDecoder = decoderProvider.getIfAvailable();
        // 仅OIDC模式需要"本人身份客户端"；local模式置null，避免无谓依赖
        AuthIdentityFeign identity = oidc ? identityProvider.getIfAvailable() : null;
        // OIDC模式的前置完整性校验：缺解码器或缺身份客户端都属于配置错误，必须启动即失败
        if (oidc && (jwtDecoder == null || identity == null)) {
            throw new IllegalStateException("OIDC模式缺少JWT解码器或本人身份客户端");
        }
        http
                // 关闭CSRF：前后端分离、Token鉴权的无状态API，CSRF Token机制不适用且会拦截跨域请求
                .csrf(AbstractHttpConfigurer::disable)
                // 无状态会话：不创建也不读取HttpSession，身份完全靠Token承载
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 默认全部接口放行：授权判断下移到方法注解(@PreAuthorize)、自定义Filter、AOP，
                // 本链只负责"身份解析"，不负责"接口是否允许访问"
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        // 只有存在JwtDecoder时才启用资源服务能力；local且未配jwks时整块跳过，保持纯放行链
        if (jwtDecoder != null) {
            http.oauth2ResourceServer(rs -> {
                // ---- OIDC专属：Bearer解析器包装 + 认证失败处理器改造 ----
                if (oidc) {
                    // 允许业务自定义Bearer解析器，未提供则用Spring默认（从Authorization头取Bearer）
                    BearerTokenResolver delegate = bearerResolverProvider.getIfAvailable(DefaultBearerTokenResolver::new);
                    // 包装一层：把 resolver、可信内网配置、以及"是否存在能力Filter"传进去，
                    // 用于区分人类Token与任务能力Token，避免能力Token被当人类身份解析
                    rs.bearerTokenResolver(new OidcBearerTokenResolver(delegate, securityProperties,
                            capabilityFilterProvider.getIfAvailable() != null));
                    // 通过ObjectPostProcessor在过滤器实例创建后替换其失败处理器
                    rs.withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                        @Override
                        public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                            // 用EntryPoint驱动的失败处理器，把认证异常统一转成HTTP响应
                            var handler = new AuthenticationEntryPointFailureHandler(jsonOidcEntryPoint());
                            // 关闭异常外抛：身份服务不可用等异常不走容器默认500，而由EntryPoint转503
                            handler.setRethrowAuthenticationServiceException(false);
                            filter.setAuthenticationFailureHandler(handler);
                            return filter;
                        }
                    });
                }
                rs.jwt(jwt -> {
                            // 绑定解码器：验签、时间戳、issuer（OIDC）等校验全部在此生效
                            jwt.decoder(jwtDecoder);
                            // OIDC模式：把OIDC的subject经远程身份服务换取系统内用户，得到可用的业务身份
                            // local模式：subject本身即可信业务身份，无需转换
                            if (oidc) { jwt.jwtAuthenticationConverter(new OidcRemoteHumanAuthenticationConverter(identity)); }
                        })
                        // 认证入口点：未携带Token或解析失败时的统一返回；
                        // local → 一律401 UNAUTHORIZED；oidc → 区分凭证错误401与身份服务不可用503
                        .authenticationEntryPoint(oidc ? jsonOidcEntryPoint() : json401EntryPoint());
            });
        }

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
     * OIDC模式专用认证入口点
     *
     * <p>
     * 语义区分：
     * - AuthenticationServiceException（身份服务本身不可用、远程调用失败）→ 503 SERVICE_UNAVAILABLE，
     *   表示"不是调用方凭证有问题，而是认证依赖不可用"，便于上游区分重试策略；
     * - 其余认证异常（凭证无效、过期、缺失）→ 回落到401统一处理。
     * </p>
     */
    private AuthenticationEntryPoint jsonOidcEntryPoint() {
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
