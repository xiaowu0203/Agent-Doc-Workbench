package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** 仅连接已授权的loopback Redis；删除已记录的随机前缀键，不扫描/清空业务键。 */
@EnabledIfEnvironmentVariable(named = "P1_LOCAL_REDIS_TEST", matches = "true")
class LocalLoginFailureLimiterRedisTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private LocalLoginFailureLimiter limiter;
    private String namespace;
    private final Set<String> ownedKeys = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void start() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 6379),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory);
        namespace = "adw:p1:login-test:" + UUID.randomUUID();
        var properties = new LocalIdentityProperties(); properties.setLoginRateNamespace(namespace);
        limiter = new LocalLoginFailureLimiter(redis, properties);
    }

    @AfterEach
    void stop() {
        try {
            assertThat(ownedKeys).allSatisfy(key -> assertThat(key).startsWith(namespace + ":"));
            if (!ownedKeys.isEmpty()) { redis.delete(ownedKeys); }
        } finally { factory.destroy(); }
    }

    @Test
    void sixthAccountFailureIsBlockedEvenWithDifferentClientIpsAndNameCasing() {
        for (int index = 0; index < 5; index++) { reserve(index % 2 == 0 ? "Account_Private_Name" : " account_private_name ", "192.0.2." + index); }
        assertLimited(() -> reserve("ACCOUNT_PRIVATE_NAME", "198.51.100.1"));
        assertThat(ownedKeys).allSatisfy(key -> assertThat(key).doesNotContain("Account_Private_Name", "account_private_name", "192.0.2"));
    }

    @Test
    void fiftyFirstIpFailureIsBlockedAcrossDifferentAccounts() {
        for (int index = 0; index < 50; index++) { reserve("account_" + index, "192.0.2.1"); }
        assertLimited(() -> reserve("different_account", "192.0.2.1"));
    }

    @Test
    void concurrentReservationsCannotExceedTheAccountLimit() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Integer>>();
            for (int index = 0; index < 30; index++) {
                futures.add(workers.submit(() -> {
                    assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
                    try { reserve("concurrent_account", "192.0.2.2"); return 1; }
                    catch (BusinessException error) { assertThat(error.getCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode()); return 0; }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (var future : futures) { accepted += future.get(3, TimeUnit.SECONDS); }
            assertThat(accepted).isEqualTo(5);
        }
    }

    @Test
    void successOnlyRemovesItsOwnReservationAndIsIdempotent() {
        var first = reserve("account", "192.0.2.3");
        for (int index = 0; index < 4; index++) { reserve("account", "192.0.2.3"); }
        limiter.release(first); reserve("account", "192.0.2.3"); limiter.release(first);
        assertThat(redis.opsForZSet().zCard(first.keys().getFirst())).isEqualTo(5);
        assertLimited(() -> reserve("account", "192.0.2.3"));
    }

    @Test
    void expiredAttemptCannotRemoveTheNewWindowReservation() {
        limiter = new LocalLoginFailureLimiter(redis, namespace, 1, Duration.ofMillis(200), 10, Duration.ofSeconds(2));
        var old = reserve("account", "192.0.2.4");
        assertLimited(() -> reserve("account", "192.0.2.4"));
        await().atMost(Duration.ofSeconds(2)).until(() -> !Boolean.TRUE.equals(redis.hasKey(old.keys().getFirst())));
        var current = reserve("account", "192.0.2.4"); limiter.release(old);
        assertThat(redis.opsForZSet().zCard(current.keys().getFirst())).isEqualTo(1);
        assertLimited(() -> reserve("account", "192.0.2.4"));
    }

    @Test
    void existingAndUnknownAccountNamesUseTheSameLimits() {
        for (String name : new String[]{"known_account", "unknown_account"}) {
            for (int index = 0; index < 5; index++) { reserve(name, "192.0.2.5"); }
            assertLimited(() -> reserve(name, "192.0.2.5"));
        }
    }

    private LocalLoginFailureLimiter.Reservation reserve(String name, String ip) {
        var reservation = limiter.reserve(name, ip); ownedKeys.addAll(reservation.keys()); return reservation;
    }

    private void assertLimited(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode()));
    }
}
