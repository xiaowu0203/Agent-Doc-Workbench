package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/** 仅在启用任务能力过滤器时，将其登记的Bearer路径留给专用验证器。 */
public final class OidcBearerTokenResolver implements BearerTokenResolver {
    private final BearerTokenResolver delegate;
    private final boolean capabilityEnabled;
    private final List<String> capabilityPaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public OidcBearerTokenResolver(BearerTokenResolver delegate, SecurityVerifyProperties properties, boolean capabilityVerifierInstalled) {
        this.delegate = delegate;
        this.capabilityEnabled = capabilityVerifierInstalled && properties.isTaskCapabilityFilterEnabled();
        this.capabilityPaths = properties.getCapabilityAuthEndpoints() == null ? List.of() : List.copyOf(properties.getCapabilityAuthEndpoints());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (capabilityEnabled && capabilityPaths.stream().anyMatch(path -> matcher.match(path, request.getRequestURI()))) {
            return null;
        }
        return delegate.resolve(request);
    }
}
