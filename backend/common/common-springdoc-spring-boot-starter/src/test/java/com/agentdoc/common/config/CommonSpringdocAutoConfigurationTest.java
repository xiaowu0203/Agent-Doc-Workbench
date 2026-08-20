package com.agentdoc.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonSpringdocAutoConfigurationTest {

    private final CommonSpringdocAutoConfiguration config = new CommonSpringdocAutoConfiguration();

    @Test
    void buildsOpenApiWithConfiguredInfoAndBearerAuth() {
        OpenApiProperties props = new OpenApiProperties("Auth Service API", "认证服务", "v0.1.0");
        OpenAPI api = config.commonOpenAPI(props);

        assertEquals("Auth Service API", api.getInfo().getTitle());
        assertEquals("认证服务", api.getInfo().getDescription());
        assertEquals("v0.1.0", api.getInfo().getVersion());

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
    }

    @Test
    void propertiesHaveDefaults() {
        OpenApiProperties props = new OpenApiProperties(null, null, null);
        assertEquals("Agent-Doc-Workbench API", props.title());
        assertEquals("v0.1.0", props.version());
    }
}
