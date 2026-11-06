package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

class OidcBearerTokenResolverTest {
    @Test
    void onlyEnabledCapabilityEndpointsDelegateBearerToDedicatedVerifier() {
        var properties = new SecurityVerifyProperties();
        var request = new MockHttpServletRequest("POST", "/mcp/tools");
        request.addHeader("Authorization", "Bearer fixture");
        assertThat(new OidcBearerTokenResolver(new DefaultBearerTokenResolver(), properties, true).resolve(request)).isEqualTo("fixture");
        properties.setTaskCapabilityFilterEnabled(true);
        assertThat(new OidcBearerTokenResolver(new DefaultBearerTokenResolver(), properties, false).resolve(request)).isEqualTo("fixture");
        var resolver = new OidcBearerTokenResolver(new DefaultBearerTokenResolver(), properties, true);
        assertThat(resolver.resolve(request)).isNull();
        var user = new MockHttpServletRequest("GET", "/api/documents");
        user.addHeader("Authorization", "Bearer fixture");
        assertThat(resolver.resolve(user)).isEqualTo("fixture");
    }
}
