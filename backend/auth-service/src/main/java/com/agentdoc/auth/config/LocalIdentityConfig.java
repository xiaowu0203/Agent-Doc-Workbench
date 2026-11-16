package com.agentdoc.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** local专属配置不成为OIDC启动依赖。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
@EnableConfigurationProperties(LocalIdentityProperties.class)
public class LocalIdentityConfig { }
