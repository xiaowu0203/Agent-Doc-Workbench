package com.agentdoc.gateway.poc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 用真实Spring Session与Redis验证条件写入模型；尚未接入正式刷新流程。 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_REDIS_POC", matches = "true")
class P0RedisLogoutRaceTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final DefaultRedisScript<Long> SAVE_IF_PRESENT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]); return 1 else return 0 end", Long.class);

    @Test
    void lateRefreshCannotRecreateDeletedSpringSession() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String namespace = "adw:p0:" + UUID.randomUUID();
        try (P0RedisSessionStore redis = new P0RedisSessionStore(namespace, key)) {
            try {
                var session = redis.sessions.createWebSession().block(TIMEOUT);
                session.getAttributes().put("p0-refresh", "old");
                session.start();
                session.save().block(TIMEOUT);
                String redisKey = namespace + ":sessions:" + session.getId();
                var serializer = new P0RedisSessionStore.EncryptedSerializer(key);
                byte[] attribute = "sessionAttr:p0-refresh".getBytes(StandardCharsets.UTF_8);
                byte[] replacement = serializer.serialize("rotated");
                assertThat(redis.raw.execute(SAVE_IF_PRESENT, List.of(redisKey), List.of(attribute, replacement))
                        .single().block(TIMEOUT)).isEqualTo(1L);
                assertThat(redis.sessions.retrieveSession(session.getId()).block(TIMEOUT)
                        .<String>getAttribute("p0-refresh")).isEqualTo("rotated");
                var ready = new CountDownLatch(1);
                var allowWrite = new CountDownLatch(1);
                try (var worker = Executors.newSingleThreadExecutor()) {
                    var lateWrite = worker.submit(() -> {
                        ready.countDown();
                        if (!allowWrite.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
                        return redis.raw.execute(SAVE_IF_PRESENT, List.of(redisKey), List.of(attribute, replacement))
                                .single().block(TIMEOUT);
                    });
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                    session.invalidate().block(TIMEOUT);
                    allowWrite.countDown();
                    assertThat(lateWrite.get(5, TimeUnit.SECONDS)).isEqualTo(0L);
                }
                assertThat(redis.raw.hasKey(redisKey).block(TIMEOUT)).isFalse();
                assertThat(redis.sessions.retrieveSession(session.getId()).block(TIMEOUT)).isNull();
            } finally {
                redis.clearFixture();
            }
        }
    }
}
