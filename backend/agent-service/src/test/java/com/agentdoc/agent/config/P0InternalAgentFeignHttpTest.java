package com.agentdoc.agent.config;

import com.agentdoc.agent.controller.InternalAgentController;
import com.agentdoc.agent.service.AgentService;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.CommonWebAutoConfiguration;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.config.TaskInternalCallProperties;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.minio.config.MinioAutoConfiguration;
import feign.Feign;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task的Feign配置通过真实HTTP访问Agent认证入口；Agent业务档案使用夹具。 */
@SpringBootTest(classes = P0InternalAgentFeignHttpTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class P0InternalAgentFeignHttpTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = MinioAutoConfiguration.class)
    @Import({InternalAgentController.class, CommonSecurityAutoConfiguration.class,
            CommonWebAutoConfiguration.class, InternalAgentSecurityConfig.class})
    static class App { }

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("agent-doc.task-internal-auth.secret", () -> SECRET);
    }

    @LocalServerPort private int port;
    @MockitoBean private AgentService service;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void taskFeignUsesIndependentCredentialToReadProfile() {
        when(service.executionProfile(9001L)).thenReturn(new AgentExecutionProfileVO(
                9001L, 9002L, 9003L, 100L, "[9004]", 1L, true, BigDecimal.ONE, BigDecimal.TEN));
        var properties = new TaskInternalCallProperties();
        properties.setSecret(SECRET);
        properties.setAgentUrl("http://localhost:" + port);
        var agent = Feign.builder().contract(new SpringMvcContract())
                .decoder(new SpringDecoder(HttpMessageConverters::new))
                .requestInterceptor(new AuthHeaderForwardInterceptor(properties))
                .target(AgentFeign.class, "http://localhost:1");
        try {
            AuthorizationContext.set("Bearer unrelated-user");
            var result = agent.getExecutionProfile(9001L);
            assertThat(result.code()).isZero();
            assertThat(result.data().agentId()).isEqualTo(9001L);
            verify(service).executionProfile(9001L);
        } finally {
            AuthorizationContext.clear();
        }
    }
}
