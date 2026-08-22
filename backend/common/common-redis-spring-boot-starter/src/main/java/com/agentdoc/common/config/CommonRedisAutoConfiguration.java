package com.agentdoc.common.config;

import com.agentdoc.common.utils.RedisUtils;
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
 * Redis 统一自动配置，归属 common‑redis‑spring‑boot‑starter。
 * <ul>
 *     <li>jsonRedisTemplate：JSON序列化RedisTemplate，替换JDK原生序列化，存储可读性更好；</li>
 *     <li>RedisUtils：Redis通用工具封装，支持string/hash、过期时间、分布式锁基础能力等。</li>
 * </ul>
 * <p>生效条件：classpath存在 {@link RedisTemplate}，且容器中已经存在 {@link RedisConnectionFactory}；
 * 依赖Spring Boot原生{@link RedisAutoConfiguration}，使用 {@link AutoConfigureAfter} 保证连接工厂优先完成初始化。
 * </p>
 * <p>覆盖策略：业务服务可自定义同名Bean覆盖默认实例；
 * 本配置不会替换Spring Boot原生默认的 {@code redisTemplate}，新增独立bean名称 jsonRedisTemplate。
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class CommonRedisAutoConfiguration {

    /**
     * 创建 JSON序列化的 RedisTemplate，Bean名称：jsonRedisTemplate。
     * <p>key/hashKey 使用字符串序列化；value/hashValue 使用 {@link GenericJackson2JsonRedisSerializer} JSON序列化。
     * 不会覆盖SpringBoot默认名称为 redisTemplate 的Bean；容器中不存在本名称Bean时才实例化。</p>
     * @param factory Redis连接工厂，由Spring Boot自动装配提供
     * @return RedisTemplate 实例，key为String，value为Object
     */
    @Bean
    @ConditionalOnMissingBean(name = "jsonRedisTemplate")
    public RedisTemplate<String, Object> jsonRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // key、hashKey 使用字符串序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        // value、hashValue 使用Jackson JSON序列化
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis通用工具类Bean，依赖 jsonRedisTemplate。
     * @param jsonRedisTemplate JSON序列化模板实例
     * @return RedisUtils 工具实例
     */
    @Bean
    @ConditionalOnMissingBean(RedisUtils.class)
    public RedisUtils redisUtils(RedisTemplate<String, Object> jsonRedisTemplate) {
        return new RedisUtils(jsonRedisTemplate);
    }
}
