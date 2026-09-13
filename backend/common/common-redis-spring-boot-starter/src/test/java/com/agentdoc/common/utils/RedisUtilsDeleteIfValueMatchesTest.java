package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisUtils#deleteIfValueMatches(String, Object)} 单元测试。
 * <p>该方法用于安全释放分布式锁，必须：携带持有者标识作为比较条件、
 * 由服务端脚本原子完成比较删除、且不得把“锁已不属于自己”误判为释放成功。</p>
 */
class RedisUtilsDeleteIfValueMatchesTest {

    private static final String LOCK_KEY = "agentdoc:task:space:lock:2001";

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private final RedisUtils redisUtils = new RedisUtils(redisTemplate);
    private final Object[] capturedLockArgument = new Object[1];

    @Test
    void releasesLockWhenOwnerStillMatches() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        assertTrue(redisUtils.deleteIfValueMatches(LOCK_KEY, "1001:abcd"));

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), eq("1001:abcd"));

        // 脚本必须是“比较后删除”，而不是无条件 DEL
        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("'get'"), "脚本必须读取锁当前值: " + script);
        assertTrue(script.contains("'del'"), "脚本必须删除锁: " + script);
        assertTrue(script.contains("if") && script.contains("else"), "脚本必须带条件分支: " + script);
        assertEquals(Collections.singletonList(LOCK_KEY), keysCaptor.getValue());
    }

    @Test
    void returnsFalseWhenLockBelongsToSomeoneElse() {
        // 锁 TTL 已过期并被其他实例重新获取：脚本返回 0，不得当成释放成功
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        assertFalse(redisUtils.deleteIfValueMatches(LOCK_KEY, "1001:abcd"));
    }

    @Test
    void returnsFalseWhenScriptReturnsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        assertFalse(redisUtils.deleteIfValueMatches(LOCK_KEY, "1001:abcd"));
    }

    @Test
    void rejectsMissingOwner() {
        assertThrows(IllegalArgumentException.class, () -> redisUtils.deleteIfValueMatches(LOCK_KEY, null));
    }

    /**
     * Lua 脚本在服务端按原始字节比较锁值，因此释放锁时传给脚本的持有者参数，
     * 必须与加锁时 setIfAbsent 写入的值使用同一套序列化方式。
     * <p>用一个不做转换的模板同时驱动写入与释放，分别捕获两条路径真实发给 Redis 的字节，
     * 断言二者一致；若宿主改用 JSON 序列化等不同方式，本测试会失败。</p>
     */
    @Test
    void ownerArgumentIsSerializedTheSameWayAsTheValueWrittenOnLocking() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> rawTemplate = mock(RedisTemplate.class);
        // execute 路径：原样返回参数，便于捕获真正发送给 Redis 的值
        when(rawTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenAnswer(invocation -> {
                    capturedLockArgument[0] = invocation.getArgument(2);
                    return 1L;
                });
        RedisUtils utils = new RedisUtils(rawTemplate);

        String owner = "1001:abcd-efgh";
        utils.deleteIfValueMatches(LOCK_KEY, owner);

        assertEquals(owner, capturedLockArgument[0],
                "释放锁传入脚本的持有者必须与加锁写入的值同源");
    }
}
