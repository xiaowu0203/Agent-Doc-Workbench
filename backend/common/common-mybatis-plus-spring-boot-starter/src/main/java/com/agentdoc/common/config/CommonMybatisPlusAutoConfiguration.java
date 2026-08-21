package com.agentdoc.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis‑Plus 公共自动配置，统一装配MyBatis‑Plus插件拦截器与公共字段填充。
 * <p>
 * 默认内置能力：
 * <ul>
 * <li>{@link PaginationInnerInterceptor}：MySQL分页插件，提供分页查询能力；</li>
 * <li>{@link OptimisticLockerInnerInterceptor}：乐观锁插件，可通过配置开关控制是否启用。</li>
 * <li>{@link CommonMetaObjectHandler}：公共字段自动填充（createdAt / updatedAt）。</li>
 * </ul>
 * <p>生效条件：classpath存在 {@link MybatisPlusInterceptor}（引入mybatis‑plus依赖）才加载；
 * 业务服务可自行定义 {@link MybatisPlusInterceptor} / {@link MetaObjectHandler} Bean，覆盖本默认实例。
 * <p>配置项：{@code agent‑doc.mybatis‑plus.optimistic‑lock‑enabled}，默认false，开启后启用乐观锁。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class CommonMybatisPlusAutoConfiguration {

    /**
     * 构建MyBatis‑Plus主拦截器，整合分页、可选乐观锁插件。
     * @param optimisticLockEnabled 是否开启乐观锁插件，读取配置 agent‑doc.mybatis‑plus.optimistic‑lock‑enabled
     * @return MybatisPlusInterceptor 拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Value("${agent-doc.mybatis-plus.optimistic-lock-enabled:false}") boolean optimisticLockEnabled) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // MySQL分页插件，必须配置否则Page对象不会生效
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 配置开启时，追加乐观锁插件，实体需要 @Version 注解配合使用
        if (optimisticLockEnabled) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
        return interceptor;
    }

    /**
     * 公共字段自动填充处理器：插入时填充 createdAt / updatedAt，更新时填充 updatedAt。
     * <p>依赖基类 {@code @TableField(fill = ...)} 注解，strict 方法仅对声明了 fill 的字段生效。
     * @return CommonMetaObjectHandler 实例
     */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler commonMetaObjectHandler() {
        return new CommonMetaObjectHandler();
    }
}
