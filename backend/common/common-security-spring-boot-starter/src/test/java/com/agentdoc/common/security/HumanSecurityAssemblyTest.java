package com.agentdoc.common.security;

import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.WorkbenchAuthProperties;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HumanSecurityAssemblyTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class, CommonSecurityAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void localDefaultsRequireExplicitIdentityTrustAndNeverCallAuthDuringAssembly() {
        runner.withUserConfiguration(IdentityClient.class)
                .withPropertyValues("agent-doc.security.identity-jwks-url=http://127.0.0.1:1/identity-jwks")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context.getBean(WorkbenchAuthProperties.class).getMode()).isEqualTo(AuthenticationMode.LOCAL);
                    verifyNoInteractions(context.getBean(AuthIdentityFeign.class));
                });
    }

    @Test
    void legacyJwksOrMissingIdentityClientCannotCreateAnUnprotectedLocalChain() {
        runner.withUserConfiguration(IdentityClient.class).withPropertyValues("agent-doc.security.jwks-url=http://127.0.0.1:1/legacy")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("agent-doc.security.identity-jwks-url=http://127.0.0.1:1/identity")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownModeAndOidcWithoutIssuerFailBeforeOpeningAChain() {
        for (String mode : new String[]{"unknown", "oidc"}) {
            runner.withUserConfiguration(IdentityClient.class).withPropertyValues("workbench.auth.mode=" + mode)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class IdentityClient {
        @Bean AuthIdentityFeign identity() { return mock(AuthIdentityFeign.class); }
    }
}
