package com.agentdoc.common.redis;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 通用 Redis 操作工具类，封装常用 String、Hash、过期时间、简单分布式锁原语。
 * <p>
 * 底层依赖 {@code jsonRedisTemplate}，使用Jackson JSON序列化存储对象；
 * </p>
 * <p>注意：{@link #setIfAbsent(String, Object, Duration)} 仅为简易NX+EX原语，
 */
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * @param redisTemplate JSON序列化的RedisTemplate实例
     */
    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ---------- String 字符串操作 ----------

    /**
     * 设置键值，无过期时间。
     * @param key redis键
     * @param value 值，支持对象，JSON序列化存储
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置键值并指定过期时间。
     * @param key redis键
     * @param value 值，支持对象
     * @param timeout 过期时长
     */
    public void set(String key, Object value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    /**
     * SET NX EX：仅key不存在时写入，同时设置过期时间。
     * <p>可用于简易分布式锁、幂等控制；
     * <strong>不支持锁重入、自动续期；复杂锁场景优先使用Redisson。</strong></p>
     * @param key redis键
     * @param value 值
     * @param timeout 锁/缓存过期时间
     * @return true 设置成功；false key已存在设置失败
     */
    public boolean setIfAbsent(String key, Object value, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout));
    }

    /**
     * 获取String类型key的值。
     * @param key redis键
     * @return 存储对象，不存在返回null
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * key数值自增delta。key不存在则初始为0再自增。
     * @param key redis键
     * @param delta 增量，可为负数实现自减
     * @return 自增之后的数值
     */
    public long increment(String key, long delta) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    // ---------- Hash 哈希操作 ----------

    /**
     * Hash设置单个field‑value。
     * @param key hash的key
     * @param field hash字段名
     * @param value 字段值，支持对象JSON序列化
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * 获取Hash中指定field的值。
     * @param key hash的key
     * @param field hash字段名
     * @return 字段值；key或field不存在返回null
     */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    // ---------- 通用key操作 ----------

    /**
     * 判断key是否存在。
     * @param key redis键
     * @return true key存在；false不存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 删除指定key。
     * @param key redis键
     * @return true 删除成功；false key不存在
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 删除指定key。
     * @param key redis键
     * @return true 删除成功；false key不存在
     */
    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout.toMillis(), TimeUnit.MILLISECONDS));
    }
}
