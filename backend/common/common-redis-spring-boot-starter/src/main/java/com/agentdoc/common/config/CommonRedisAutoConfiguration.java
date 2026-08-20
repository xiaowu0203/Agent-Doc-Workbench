package com.agentdoc.common.config;

import com.agentdoc.common.redis.RedisUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 统一自动配置（common-redis-spring-boot-starter）：
 * <ul>
 *     <li>jsonRedisTemplate：JSON 序列化模板（区别于 Boot 默认 JdkSerializationRedisTemplate）</li>
 *     <li>RedisUtils：通用 Redis 操作工具（string/hash、TTL、分布式锁原语等）</li>
 * </ul>
 * 条件装配：仅当存在 RedisConnectionFactory（即应用引入了 Spring Data Redis 且配置了连接）时生效，
 * 装配顺序在 RedisAutoConfiguration 之后，确保连接工厂 Bean 已注册。
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class CommonRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "jsonRedisTemplate")
    public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(RedisUtils.class)
    public RedisUtils redisUtils(RedisTemplate<String, Object> jsonRedisTemplate) {
        return new RedisUtils(jsonRedisTemplate);
    }
}
