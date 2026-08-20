package com.agentdoc.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 统一自动装配（common-mybatis-plus-spring-boot-starter）：
 * <ul>
 *     <li>分页插件：PaginationInnerInterceptor（MySQL），默认启用</li>
 *     <li>乐观锁插件：OptimisticLockerInnerInterceptor，默认关闭
 *         （当前表结构无 version 列；需要时配置 agent-doc.mybatis-plus.optimistic-lock-enabled=true
 *          并给目标表增加 @Version 字段）</li>
 * </ul>
 * 逻辑删除与雪花 ID 通过 BaseEntity / BaseLogicDeleteEntity 上的字段注解生效，无需全局配置。
 * 服务可自行定义 MybatisPlusInterceptor Bean 覆盖（@ConditionalOnMissingBean）。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class CommonMybatisPlusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Value("${agent-doc.mybatis-plus.optimistic-lock-enabled:false}") boolean optimisticLockEnabled) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        if (optimisticLockEnabled) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
        return interceptor;
    }
}
