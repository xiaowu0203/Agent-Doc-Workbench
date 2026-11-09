package com.agentdoc.agent.config;

import com.agentdoc.common.config.TaskInternalAuthProperties;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.filter.TaskInternalAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** P0只收紧Task执行档案入口，其余业务授权仍由原领域入口负责。 */
@Configuration
@EnableConfigurationProperties(TaskInternalAuthProperties.class)
public class InternalAgentSecurityConfig {

    @Bean
    public TaskInternalAuthenticationFilter taskInternalAuthenticationFilter(TaskInternalAuthProperties properties) {
        return new TaskInternalAuthenticationFilter(properties, List.of(TaskInternalAuthConstant.EXECUTION_PROFILE_PATH));
    }

    @Bean
    public FilterRegistrationBean<TaskInternalAuthenticationFilter> taskInternalFilterRegistration(
            TaskInternalAuthenticationFilter filter) {
        FilterRegistrationBean<TaskInternalAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
