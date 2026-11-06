package com.agentdoc.gateway.poc;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.Assert;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.web.server.session.WebSessionStore;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/** P0隔离原型，不参与正式Gateway组件扫描；无业务权限策略。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableWebFlux
@EnableWebFluxSecurity
@Import({P0BffPocConfiguration.Local.class, P0BffPocConfiguration.Oidc.class})
class P0BffPocConfiguration {

    static final Duration WINDOW = Duration.ofDays(7);
    static final String COOKIE = "adw-poc-session";
    static final String MODE = "poc.mode";
    static final String EXPIRES = "poc.expires";
    static final String REMEMBER = "poc.remember";
    static final String COOKIE_TTL = "poc.cookie-ttl";

    enum Mode { LOCAL, OIDC }

    /** 模拟远程Auth身份/投影契约；实际绑定规则由测试夹具承担，不写在BFF内。 */
    interface IdentityProjection {
        Mono<Map<String, Object>> resolve(Authentication authentication);
    }

    @Bean
    Mode mode(Environment environment) {
        return Mode.valueOf(environment.getProperty("workbench.auth.mode", "local").toUpperCase(Locale.ROOT));
    }

    @Bean
    WebSessionManager webSessionManager(ObjectProvider<WebSessionStore> stores) {
        DefaultWebSessionManager manager = new DefaultWebSessionManager();
        stores.ifAvailable(manager::setSessionStore);
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver() {
            @Override
            public void setSessionId(ServerWebExchange exchange, String id) {
                exchange.getResponse().addCookie(ResponseCookie.from(COOKIE, id).path("/")
                        .httpOnly(true).sameSite("Lax")
                        .maxAge(exchange.getAttributeOrDefault(COOKIE_TTL, Duration.ofSeconds(-1))).build());
            }
        };
        resolver.setCookieName(COOKIE);
        resolver.addCookieInitializer(cookie -> cookie.path("/").httpOnly(true).sameSite("Lax"));
        manager.setSessionIdResolver(resolver);
        return manager;
    }

    @Bean
    WebSessionServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    @Order(-200)
    WebFilter sessionBoundary(Mode mode, Clock clock) {
        return (exchange, chain) -> exchange.getSession().flatMap(session -> {
            if (session.getAttributes().containsKey("SPRING_SECURITY_CONTEXT")
                    && (session.getAttribute(MODE) != mode || session.getAttribute(EXPIRES) == null
                    || !clock.instant().isBefore(session.<Instant>getAttribute(EXPIRES)))) {
                return session.invalidate().then(finish(exchange, HttpStatus.UNAUTHORIZED));
            }
            session.setMaxIdleTime(WINDOW);
            if (exchange.getRequest().getPath().value().equals("/oauth2/authorization/workbench") && mode == Mode.OIDC) {
                session.getAttributes().put(REMEMBER,
                        "true".equals(exchange.getRequest().getQueryParams().getFirst("remember")));
            }
            if (Boolean.TRUE.equals(session.getAttribute(REMEMBER)) && session.getAttribute(EXPIRES) != null) {
                exchange.getAttributes().put(COOKIE_TTL,
                        Duration.between(clock.instant(), session.<Instant>getAttribute(EXPIRES)));
            }
            return chain.filter(exchange);
        });
    }

    @Bean
    ServerAuthenticationSuccessHandler loginSuccess(Mode mode, Clock clock, IdentityProjection projection) {
        return (filterExchange, authentication) -> projection.resolve(authentication)
                .flatMap(ignored -> filterExchange.getExchange().getSession().flatMap(session -> {
                    Mono<Boolean> remember = mode == Mode.LOCAL
                            ? filterExchange.getExchange().getFormData()
                            .map(form -> "true".equals(form.getFirst("remember")))
                            : Mono.just(Boolean.TRUE.equals(session.getAttribute(REMEMBER)));
                    return remember.flatMap(persistent -> {
                        session.getAttributes().put(MODE, mode);
                        session.getAttributes().put(EXPIRES, clock.instant().plus(WINDOW));
                        session.getAttributes().put(REMEMBER, persistent);
                        filterExchange.getExchange().getAttributes().put(COOKIE_TTL,
                                persistent ? WINDOW : Duration.ofSeconds(-1));
                        return finish(filterExchange.getExchange(), HttpStatus.NO_CONTENT);
                    });
                }))
                .onErrorResume(AccessDeniedException.class, error -> filterExchange.getExchange().getSession()
                        .flatMap(WebSession::invalidate).then(finish(filterExchange.getExchange(), HttpStatus.FORBIDDEN)));
    }

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, ObjectProvider<LocalLogin> local,
                                    ObjectProvider<ReactiveClientRegistrationRepository> registrations,
                                    ObjectProvider<ServerOAuth2AuthorizedClientRepository> clients,
                                    WebSessionServerSecurityContextRepository contexts,
                                    ServerAuthenticationSuccessHandler success) {
        ReactiveClientRegistrationRepository oidc = registrations.getIfAvailable();
        OidcClientInitiatedServerLogoutSuccessHandler sso = oidc == null ? null
                : new OidcClientInitiatedServerLogoutSuccessHandler(oidc);
        if (sso != null) {
            sso.setPostLogoutRedirectUri("{baseUrl}/logged-out");
        }
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(contexts)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((exchange, error) -> finish(exchange, HttpStatus.UNAUTHORIZED)))
                .authorizeExchange(access -> access.pathMatchers("/api/auth/config", "/api/csrf", "/api/session/login",
                                "/oauth2/**", "/login/oauth2/**", "/logged-out").permitAll()
                        .anyExchange().authenticated())
                .logout(logout -> logout.requiresLogout(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                                oidc == null ? new String[]{"/api/session/logout"}
                                        : new String[]{"/api/session/logout", "/api/session/logout-sso"}))
                        .logoutHandler(new SecurityContextServerLogoutHandler())
                        // Security随后追加CSRF清理（会更换并保存Session ID），最后才能删除Redis会话。
                        .logoutSuccessHandler((exchange, authentication) -> exchange.getExchange().getSession()
                                .flatMap(WebSession::invalidate).then(Mono.defer(() -> sso != null
                                        && exchange.getExchange().getRequest().getPath().value().endsWith("/logout-sso")
                                        ? sso.onLogoutSuccess(exchange, authentication)
                                        : finish(exchange.getExchange(), HttpStatus.NO_CONTENT)))));
        local.ifAvailable(login -> http.addFilterAt(login.filter(), SecurityWebFiltersOrder.AUTHENTICATION));
        registrations.ifAvailable(repository -> {
            DefaultServerOAuth2AuthorizationRequestResolver resolver = new DefaultServerOAuth2AuthorizationRequestResolver(repository);
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(login -> login.clientRegistrationRepository(repository).authorizedClientRepository(clients.getObject())
                    .authorizationRequestResolver(resolver).securityContextRepository(contexts)
                    .authenticationSuccessHandler(success)
                    .authenticationFailureHandler((exchange, error) -> {
                        if (error instanceof OAuth2AuthenticationException oauth) {
                            exchange.getExchange().getResponse().getHeaders().set("X-Poc-Failure", oauth.getError().getErrorCode());
                        }
                        return exchange.getExchange().getSession().flatMap(WebSession::invalidate)
                                .then(finish(exchange.getExchange(), HttpStatus.UNAUTHORIZED));
                    }));
        });
        return http.build();
    }

    @Bean
    RouterFunction<ServerResponse> routes(Mode mode, IdentityProjection projection) {
        return route().GET("/api/auth/config", request -> ServerResponse.ok().bodyValue(Map.of("mode", mode.name().toLowerCase(Locale.ROOT))))
                .GET("/api/csrf", request -> {
                    Mono<CsrfToken> token = request.exchange().getAttribute(CsrfToken.class.getName());
                    return token.flatMap(value -> ServerResponse.ok().bodyValue(Map.of("token", value.getToken(), "headerName", value.getHeaderName())));
                })
                .GET("/api/session", request -> request.principal().cast(Authentication.class).flatMap(projection::resolve)
                        .flatMap(profile -> request.session().flatMap(session -> ServerResponse.ok().bodyValue(
                                Map.of("user", profile, "expiresAt", session.<Instant>getAttribute(EXPIRES).toString())))))
                .POST("/api/probe/write", request -> ServerResponse.noContent().build())
                .GET("/logged-out", request -> ServerResponse.ok().bodyValue("logged out")).build();
    }

    private static Mono<Void> finish(ServerWebExchange exchange, HttpStatus status) {
        // 注销处理器先清会话，再提交响应；不能在组装then链时提前触发会话保存。
        return Mono.defer(() -> {
            exchange.getResponse().setStatusCode(status);
            return exchange.getResponse().setComplete();
        });
    }

    /** 避免AuthenticationWebFilter作为全局WebFilter被再次注册。 */
    record LocalLogin(AuthenticationWebFilter filter) { }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "workbench.auth.mode", havingValue = "local", matchIfMissing = true)
    static class Local {
        @Bean
        LocalLogin localLogin(@Qualifier("pocLocalAuthenticator") ReactiveAuthenticationManager manager,
                                            WebSessionServerSecurityContextRepository contexts,
                                            ServerAuthenticationSuccessHandler success) {
            AuthenticationWebFilter filter = new AuthenticationWebFilter(manager);
            filter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/api/session/login"));
            filter.setServerAuthenticationConverter(exchange -> exchange.getFormData().map(form ->
                    UsernamePasswordAuthenticationToken.unauthenticated(form.getFirst("username"), form.getFirst("password"))));
            filter.setSecurityContextRepository(contexts);
            filter.setAuthenticationSuccessHandler(success);
            filter.setAuthenticationFailureHandler((exchange, error) -> finish(exchange.getExchange(), HttpStatus.UNAUTHORIZED));
            return new LocalLogin(filter);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "workbench.auth.mode", havingValue = "oidc")
    static class Oidc {
        @Bean
        ReactiveClientRegistrationRepository registrations(Environment environment) {
            String issuer = environment.getRequiredProperty("poc.issuer");
            String secret = environment.getRequiredProperty("poc.client-secret");
            Assert.hasText(secret, "OIDC client secret is required");
            ClientRegistration registration = ClientRegistration.withRegistrationId("workbench")
                    .clientId("workbench-web").clientSecret(secret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).scope("openid", "workbench:access")
                    .redirectUri("{baseUrl}/login/oauth2/code/workbench").issuerUri(issuer)
                    .authorizationUri(issuer + "/protocol/openid-connect/auth")
                    .tokenUri(issuer + "/protocol/openid-connect/token").jwkSetUri(issuer + "/protocol/openid-connect/certs")
                    .userInfoUri(issuer + "/protocol/openid-connect/userinfo").userNameAttributeName("sub")
                    .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/protocol/openid-connect/logout"))
                    .clientName("P0").build();
            return new InMemoryReactiveClientRegistrationRepository(registration);
        }

        @Bean
        ServerOAuth2AuthorizedClientRepository clients() {
            return new WebSessionServerOAuth2AuthorizedClientRepository();
        }
    }
}
