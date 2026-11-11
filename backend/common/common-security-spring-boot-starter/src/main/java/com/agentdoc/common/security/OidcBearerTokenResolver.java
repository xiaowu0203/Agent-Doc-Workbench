package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 仅在启用任务能力过滤器时，将其登记的Bearer路径留给专用验证器。
 * <p>
 * P0用于OIDC，P1在local/oidc共用的Bearer路由器：
 * 当请求命中能力令牌专属端点列表、并且任务能力Filter已开启时，返回null，
 * 让Spring Security默认OIDC Bearer解析逻辑直接跳过该请求，交给{@link TaskCapabilityAuthenticationFilter}独立解析X-TASK-CAPABILITY/Bearer能力令牌；
 * 其余路径走原有delegate（默认解析器），正常解析人类OIDC令牌。
 * </p>
 */
public final class OidcBearerTokenResolver implements BearerTokenResolver {

    private final BearerTokenResolver delegate;
    private final boolean capabilityEnabled;
    private final List<String> capabilityPaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public OidcBearerTokenResolver(BearerTokenResolver delegate, SecurityVerifyProperties properties, boolean capabilityVerifierInstalled) {
        this.delegate = delegate;
        // 同时满足：能力验签器已装配 + 配置开关开启任务能力Filter
        this.capabilityEnabled = capabilityVerifierInstalled && properties.isTaskCapabilityFilterEnabled();
        this.capabilityPaths = properties.getCapabilityAuthEndpoints() == null ? List.of() : List.copyOf(properties.getCapabilityAuthEndpoints());
    }

    /**
     * 令牌解析路由逻辑：
     * 命中能力端点且能力Filter启用 → 返回null，跳过OIDC JWT解码器，交给能力Filter接管；
     * 其他请求 → 委托原始Bearer解析器，走OIDC人类令牌校验链路。
     */
    @Override
    public String resolve(HttpServletRequest request) {
        if (capabilityEnabled && capabilityPaths.stream().anyMatch(path -> matcher.match(path, request.getRequestURI()))) {
            return null;
        }
        return delegate.resolve(request);
    }
}
