package com.agentdoc.auth.config;

import com.agentdoc.common.config.WorkbenchAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Locale;

/** 认证边界统一使用UTC时钟，测试可替换为固定时钟。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkbenchAuthProperties.class)
@Slf4j
public class AuthenticationClockConfig {

    @Bean
    ApplicationRunner logAuthenticationMode(WorkbenchAuthProperties properties) {
        return arguments -> log.info("认证模式={}", properties.getMode().name().toLowerCase(Locale.ROOT));
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock authenticationClock() {
        return Clock.systemUTC();
    }
}
