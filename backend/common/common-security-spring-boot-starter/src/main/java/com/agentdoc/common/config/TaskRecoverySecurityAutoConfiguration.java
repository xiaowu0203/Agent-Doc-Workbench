package com.agentdoc.common.config;

import com.agentdoc.common.security.TaskRecoveryVerifier;
import com.agentdoc.common.filter.TaskRecoveryTransportFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 专用恢复 verifier 不复用禁止恢复凭证的普通 JwtDecoder。 */
@AutoConfiguration(after = CommonSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityVerifyProperties.class)
public class TaskRecoverySecurityAutoConfiguration {
    @Bean
    public TaskRecoveryTransportFilter taskRecoveryTransportFilter(SecurityVerifyProperties properties) {
        return new TaskRecoveryTransportFilter(properties);
    }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "jwks-url")
    public TaskRecoveryVerifier taskRecoveryVerifier(SecurityVerifyProperties properties) {
        return new TaskRecoveryVerifier(NimbusJwtDecoder.withJwkSetUri(properties.getJwksUrl()).build(), properties);
    }
}
