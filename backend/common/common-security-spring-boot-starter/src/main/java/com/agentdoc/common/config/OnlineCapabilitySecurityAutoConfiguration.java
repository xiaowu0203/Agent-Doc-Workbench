package com.agentdoc.common.config;

import com.agentdoc.common.filter.OnlineCapabilityTransportFilter;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 线上专用 verifier 只做固定凭证协议校验，不包含实验业务。 */
@AutoConfiguration(after = CommonSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityVerifyProperties.class)
public class OnlineCapabilitySecurityAutoConfiguration {
    @Bean
    public OnlineCapabilityTransportFilter onlineCapabilityTransportFilter() { return new OnlineCapabilityTransportFilter(); }
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-doc.security", name = "jwks-url")
    public OnlineCapabilityVerifier onlineCapabilityVerifier(SecurityVerifyProperties properties) {
        return new OnlineCapabilityVerifier(NimbusJwtDecoder.withJwkSetUri(properties.getJwksUrl()).build(), properties);
    }
}
