package com.agentdoc.agent.config;

import com.agentdoc.common.config.TaskInternalAuthProperties;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.filter.TaskInternalAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 内部Agent安全配置类
 * 设计说明：P0阶段范围收敛策略
 * 只单独收紧【Task执行档案】这一个接口入口的内部鉴权；
 * 系统里其余所有业务接口、领域接口的授权逻辑保持不动，仍然走原有领域鉴权体系，不做改动。
 * 该配置注册内部调用鉴权Filter，但默认关闭Filter自动注册到Tomcat全局链路，
 * 采用手动方式挂载到指定路由，避免Filter全局生效误伤其他接口。
 */
@Configuration
// 开启配置属性绑定，把yml中 agent-doc.task-internal-auth 配置绑定到 TaskInternalAuthProperties
@EnableConfigurationProperties(TaskInternalAuthProperties.class)
public class InternalAgentSecurityConfig {

    /**
     * 构造内部调用认证过滤器 Bean
     *
     * @param properties 注入 yml 上的 task-internal-auth 鉴权配置（secret、trusted-encrypted-network）
     * @return TaskInternalAuthenticationFilter 内部鉴权过滤器实例
     * 第二个参数：白名单/需要拦截校验的路径列表
     * 当前仅传入 TaskInternalAuthConstant.EXECUTION_PROFILE_PATH，
     * 代表：这个Filter只对【Task执行档案】接口做内部凭据校验
     */
    @Bean
    public TaskInternalAuthenticationFilter taskInternalAuthenticationFilter(TaskInternalAuthProperties properties) {
        return new TaskInternalAuthenticationFilter(properties, List.of(TaskInternalAuthConstant.EXECUTION_PROFILE_PATH));
    }

    /**
     * Filter注册器Bean
     * 关键点：registration.setEnabled(false);
     * FilterRegistrationBean 默认行为：把Filter注册到Servlet全局，所有请求都会经过这个Filter
     * setEnabled(false) = 关闭Servlet容器自动注册，不让它全局生效
     * 为什么这么做：
     * 1. 我们只想要这个Filter【精准拦截指定少数接口】（执行档案），不是全部接口
     * 2. 如果打开自动注册，所有请求都会进入该Filter校验内部secret，会破坏原有业务授权
     * 3. 后续一般是通过 Spring Security 的 HttpSecurity.addFilterBefore / addFilterAfter
     *    手动把这个Filter挂载到指定匹配路由上，实现局部路由拦截
     *
     * @param filter 上面定义好的内部鉴权过滤器实例
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<TaskInternalAuthenticationFilter> taskInternalFilterRegistration(
            TaskInternalAuthenticationFilter filter) {
        FilterRegistrationBean<TaskInternalAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        // 禁用Servlet自动注册，禁止全局生效
        registration.setEnabled(false);
        return registration;
    }
}
