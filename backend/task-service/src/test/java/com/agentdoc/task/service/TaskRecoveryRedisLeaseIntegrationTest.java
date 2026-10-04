package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.CommonRedisAutoConfiguration;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskRecoveryAuthFeign;
import com.agentdoc.common.feign.TaskRecoveryDocumentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.TaskRecoveryRemoteVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.A2aProperties;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.a2a.A2aTaskReconciliationService;
import com.agentdoc.task.a2a.A2aTaskSynchronizationService;
import com.agentdoc.task.a2a.TaskRecoveryClient;
import com.agentdoc.task.config.TaskRecoveryProperties;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.TaskRecoveryReason;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 显式启用的真实 Redis 组件集成：使用生产恢复/对账入口和 55 秒锁租期。
 * 仅使用随机测试锁，Mapper/Auth/Agent/Document 为桩，不冒充真实 MySQL 跨服务故障演练。
 */
@EnabledIfEnvironmentVariable(named = "P5_RECOVERY_REDIS_INTEGRATION", matches = "true")
class TaskRecoveryRedisLeaseIntegrationTest {
    private static final Long HUMAN_ID = 42L;
    private LettuceConnectionFactory factory;
    private RedisUtils redis;
    private final TaskMapper mapper = mock(TaskMapper.class);
    private final TaskCapabilityCryptoService crypto = mock(TaskCapabilityCryptoService.class);
    private final TaskRecoveryAuthFeign auth = mock(TaskRecoveryAuthFeign.class);
    private final TaskRecoveryDocumentFeign drafts = mock(TaskRecoveryDocumentFeign.class);
    private final TaskRecoveryClient client = mock(TaskRecoveryClient.class);
    private final A2aTaskSynchronizationService synchronization = mock(A2aTaskSynchronizationService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private TaskEntity task;
    private TaskRecoveryService recovery;
    private A2aTaskReconciliationService reconciliation;
    private String lockKey;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) { configuration.setPassword(RedisPassword.of(password)); }
        factory = new LettuceConnectionFactory(configuration,
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
        factory.afterPropertiesSet();
        factory.start();
        redis = new RedisUtils(new CommonRedisAutoConfiguration().jsonRedisTemplate(factory));
        task = new TaskEntity();
        // 随机负 ID 只存在于 Mockito 对象，避免与任何业务 Task 锁相交。
        task.setId(-Math.abs(UUID.randomUUID().getMostSignificantBits()));
        task.setAgentId(2L);
        task.setSpaceId(3L);
        task.setDocumentId(4L);
        task.setDocumentType(DocType.DRAFT.getCode());
        task.setExecutionMode("LIVE");
        task.setStatus(TaskStatus.RUNNING.getCode());
        task.setA2aTaskId("redis-component-test");
        task.setA2aContextId("test-context");
        task.setAgentExecutionId(5L);
        task.setCapabilityToken("test-only-encrypted-proof");
        // 使用生产前缀和随机负 ID；清理只限本测试刚创建的精确 key。
        lockKey = RedisKeyConstants.TASK_A2A_RECONCILE_LOCK_PREFIX + task.getId();
        when(mapper.selectById(task.getId())).thenReturn(task);
        String payload = "{\"exp\":" + Instant.now().minusSeconds(60).getEpochSecond() + "}";
        when(crypto.decrypt(task.getCapabilityToken())).thenReturn("test."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".test");
        when(auth.issueRecovery(anyString(), any())).thenReturn(Result.ok("test-query-credential"));
        when(auth.issueFinalization(anyString(), any())).thenReturn(Result.ok("test-draft-credential"));
        when(drafts.finalizeDraft(eq(task.getId()), anyString())).thenReturn(Result.ok());
        when(documents.checkSpacePermission(any(), anyString())).thenReturn(Result.ok());
        when(synchronization.synchronizeRecovered(eq(task), any(), any(), any())).thenAnswer(invocation -> {
            BooleanSupplier owner = invocation.getArgument(3);
            assertThat(owner.getAsBoolean()).isTrue();
            task.setStatus(TaskStatus.COMPLETED.getCode());
            return true;
        });
        TaskRecoveryProperties properties = new TaskRecoveryProperties();
        properties.setEnabled(true);
        properties.setMachineKey("test-only-machine-key-not-a-secret-32-bytes");
        recovery = new TaskRecoveryService(mapper, crypto, properties, auth, drafts, client,
                synchronization, audit, documents, redis);
        TaskRecoveryService autoRecovery = new TaskRecoveryService(mapper, crypto, properties, auth, drafts, client,
                synchronization, audit, documents, redis);
        reconciliation = new A2aTaskReconciliationService(mapper, mock(A2aTaskClient.class), synchronization,
                crypto, redis, new A2aProperties(), autoRecovery);
        assertThat(redis.hasKey(lockKey)).isFalse();
    }

    @AfterEach
    void tearDown() {
        if (redis != null && lockKey != null) { redis.delete(lockKey); }
        if (factory != null) { factory.destroy(); }
    }

    @Test
    @Timeout(15)
    void autoAndManualUseSameRealRedisLockAndOnlyOneCanWriteBack() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.query(anyString(), anyString(), anyBoolean())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return completedRemote();
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var manual = workers.submit(() -> {
                try (var user = mockStatic(AuthUtils.class)) {
                    user.when(AuthUtils::getUserIdOrException).thenReturn(HUMAN_ID);
                    return recovery.recoverManually(task.getId());
                }
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                String owner = (String) redis.get(lockKey);
                assertThat(owner).isNotBlank();
                workers.submit(() -> ReflectionTestUtils.invokeMethod(reconciliation, "reconcile", task)).get(5, TimeUnit.SECONDS);
                assertThat(redis.get(lockKey)).isEqualTo(owner);
                verify(client, times(1)).query(anyString(), anyString(), anyBoolean());
            } finally { release.countDown(); }
            assertThat(manual.get(5, TimeUnit.SECONDS).taskStatus()).isEqualTo(TaskStatus.COMPLETED.getCode());
            assertThat(redis.hasKey(lockKey)).isFalse();
            verify(drafts, times(1)).finalizeDraft(eq(task.getId()), anyString());
            verify(synchronization, times(1)).synchronizeRecovered(eq(task), any(), any(), any());
        }
    }

    @Test
    @Timeout(85)
    void realLeaseExpiryLetsAutoTakeOverAndOldManualCannotWriteOrDeleteNewOwnersLock() throws Exception {
        assertThat(TaskConstant.A2A_RECONCILE_LOCK_SECONDS).isEqualTo(55);
        assertThat(TaskRecoveryConstant.PROCESS_BUDGET_SECONDS).isEqualTo(45);
        CountDownLatch oldEntered = new CountDownLatch(1), newEntered = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1), releaseNew = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(client.query(anyString(), anyString(), anyBoolean())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                oldEntered.countDown();
                assertThat(releaseOld.await(70, TimeUnit.SECONDS)).isTrue();
            } else {
                newEntered.countDown();
                assertThat(releaseNew.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return completedRemote();
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var old = workers.submit(() -> {
                try (var user = mockStatic(AuthUtils.class)) {
                    user.when(AuthUtils::getUserIdOrException).thenReturn(HUMAN_ID);
                    return recovery.recoverManually(task.getId());
                }
            });
            try {
                assertThat(oldEntered.await(5, TimeUnit.SECONDS)).isTrue();
                String oldOwner = (String) redis.get(lockKey);
                long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                while (redis.hasKey(lockKey) && System.nanoTime() < deadline) { Thread.sleep(100); }
                assertThat(redis.hasKey(lockKey)).isFalse(); // 自然过期，不调用 EXPIRE/DEL 制造接管。
                var successor = workers.submit(() -> ReflectionTestUtils.invokeMethod(reconciliation, "reconcile", task));
                assertThat(newEntered.await(5, TimeUnit.SECONDS)).isTrue();
                String newOwner = (String) redis.get(lockKey);
                assertThat(newOwner).isNotBlank().isNotEqualTo(oldOwner);
                releaseOld.countDown();
                assertThat(old.get(5, TimeUnit.SECONDS).taskStatus()).isEqualTo(TaskStatus.RUNNING.getCode());
                assertThat(redis.get(lockKey)).isEqualTo(newOwner);
                verify(audit, atLeastOnce()).recordRecovery(any(), eq(task.getId()), eq(AuditAction.TASK_RECOVERY_FINISHED),
                        argThat(event -> event.reason() == TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED));
                verify(drafts, times(0)).finalizeDraft(any(), anyString());
                releaseNew.countDown();
                successor.get(5, TimeUnit.SECONDS);
                assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED.getCode());
                assertThat(redis.hasKey(lockKey)).isFalse();
                verify(drafts, times(1)).finalizeDraft(eq(task.getId()), anyString());
                verify(synchronization, times(1)).synchronizeRecovered(eq(task), any(), any(), any());
            } finally { releaseOld.countDown(); releaseNew.countDown(); }
        }
    }

    private TaskRecoveryRemoteVO<Task> completedRemote() {
        Task remote = Task.builder().id(task.getA2aTaskId()).contextId(task.getA2aContextId())
                .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_COMPLETED)).build();
        return new TaskRecoveryRemoteVO<>(remote, new AgentExecutionTokenUsageVO(5L, 6L, 1L,
                BigDecimal.ZERO, BigDecimal.ZERO, "CNY", 1, LocalDateTime.now(), 10L, false, 0L, false, 20L, false));
    }
}
