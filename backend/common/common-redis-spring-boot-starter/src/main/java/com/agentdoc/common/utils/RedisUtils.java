package com.agentdoc.common.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 通用 Redis 操作工具类，封装常用 String、Hash、过期时间、简单分布式锁原语。
 * <p>
 * 底层依赖 {@code jsonRedisTemplate}，使用Jackson JSON序列化存储对象；
 * </p>
 * <p>注意：{@link #setIfAbsent(String, Object, Duration)} 仅为简易NX+EX原语，
 */
public class RedisUtils {

    /**
     * 仅当 key 当前值等于期望值时才删除：命中返回 1，否则返回 0。
     * <p>比较在 Redis 服务端按原始字节进行，因此期望值必须先用与写入相同的序列化器处理。</p>
     */
    private static final DefaultRedisScript<Long> DELETE_IF_VALUE_MATCHES_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

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
     * 安全释放分布式锁：仅当 key 当前值仍等于本次加锁时写入的持有者标识时才删除。
     * <p>
     * 与 {@link #delete(String)} 的区别：锁 TTL 过期后可能已被其他实例重新获取，
     * 此时直接删除会误删他人的锁，使本应互斥的操作并发执行。本方法通过 Lua 脚本
     * 在 Redis 服务端原子完成“比较 + 删除”，避免读-比较-删除竞态。
     * </p>
     * <p>
     * 期望值必须与 {@link #setIfAbsent(String, Object, Duration)} 写入的值序列化后完全一致，
     * 本方法通过 {@link RedisScript} 重载交给模板按同一套 key / value 序列化方式处理。
     * </p>
     * @param key   锁key
     * @param owner 本次加锁写入的持有者标识
     * @return true 锁确实属于自己并已释放；false 锁已过期或已被他人持有，未做任何修改
     * @throws IllegalArgumentException 未提供持有者标识
     */
    public boolean deleteIfValueMatches(String key, Object owner) {
        if (owner == null) {
            throw new IllegalArgumentException("释放分布式锁必须提供持有者标识");
        }
        // execute(RedisScript, List<K>, Object...) 会分别用 key / value 序列化器处理参数，
        // 与 setIfAbsent 的写入方式一致，因此这里直接传原始 key 与持有者标识
        Long result = redisTemplate.execute(DELETE_IF_VALUE_MATCHES_SCRIPT, Collections.singletonList(key), owner);
        return result != null && result > 0;
    }

    /**
     * 设置key过期时间。
     * @param key redis键
     * @param timeout 过期时长
     * @return true 设置成功；false key不存在
     */
    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout.toMillis(), TimeUnit.MILLISECONDS));
    }
}
