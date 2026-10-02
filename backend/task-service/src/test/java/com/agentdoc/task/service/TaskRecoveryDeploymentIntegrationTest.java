package com.agentdoc.task.service;

import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.TaskServiceApplication;
import com.agentdoc.task.a2a.A2aTaskReconciliationService;
import com.agentdoc.task.a2a.TaskRecoveryClient;
import com.agentdoc.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * 显式启用的部署集成：真实 MySQL、Redis、Auth、Agent、Document 和用户登录/权限。
 * 仅接收已过期的 LIVE 版本冲突专用样本，不创建 Task、不调用模型、不改原证明或草稿。
 * Spy 只在真实远端查询返回后设置线程屏障，模拟处理者停顿；响应和业务依赖均不伪造。
 */
@EnabledIfEnvironmentVariable(named = "P5_RECOVERY_DEPLOYMENT_INTEGRATION", matches = "true")
@SpringBootTest(classes = TaskServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.listener.direct.auto-startup=false",
                "spring.rabbitmq.dynamic=false", "logging.level.root=ERROR", "logging.level.com.agentdoc=ERROR",
                "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@Import(TaskRecoveryDeploymentIntegrationTest.NoBackgroundWork.class)
class TaskRecoveryDeploymentIntegrationTest {
    @Autowired private TaskMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RedisUtils redis;
    @Autowired private A2aTaskReconciliationService reconciliation;
    @Autowired private TaskRecoveryService recovery;
    @MockitoSpyBean private TaskRecoveryClient client;
    @LocalServerPort private int port;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @DynamicPropertySource
    static void deploymentProperties(DynamicPropertyRegistry properties) {
        // 显式覆盖普通单测的 H2 配置，防止组件替身被误计为部署验证。
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.url", () -> "jdbc:mysql://localhost:3306/agent_doc_workbench?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true");
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_USERNAME", "root"));
        properties.add("spring.datasource.password", () -> System.getenv("MYSQL_PASSWORD"));
        properties.add("agent-doc.security.task-capability-key", () -> System.getenv("TASK_CAPABILITY_KEY"));
        properties.add("agent-doc.task-recovery.enabled", () -> true);
        properties.add("agent-doc.task-recovery.machine-key", () -> System.getenv("TASK_RECOVERY_MACHINE_KEY"));
        properties.add("agent-doc.task-recovery.auth-url", () -> "http://127.0.0.1:8081");
        properties.add("agent-doc.task-recovery.agent-url", () -> "http://127.0.0.1:8084");
        properties.add("agent-doc.task-recovery.document-url", () -> "http://127.0.0.1:8082");
    }

    @TestConfiguration
    static class NoBackgroundWork {
        /** 仅关闭验证上下文自己的调度器；维护者的 Task 服务自动对账保持运行。 */
        @Bean static BeanFactoryPostProcessor disableTestSchedulers() {
            return factory -> {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
                String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(name)) { registry.removeBeanDefinition(name); }
            };
        }
    }

    @Test
    @Timeout(110)
    void realDependenciesProtectConflictSampleDuringCompetitionAndNaturalLeaseTakeover() throws Exception {
        String selected = System.getenv("P5_RECOVERY_TASK_ID");
        assertThat(selected != null && selected.matches("\\d+")).isTrue();
        long taskId = Long.parseLong(selected);
        var task = mapper.selectById(taskId);
        assertThat(task != null && "LIVE".equals(task.getExecutionMode()) && task.getStatus() == 5).isTrue();
        assertThat(recovery.hasExpiredProof(task)).isTrue();
        Map<String, Object> before = fingerprint(taskId);
        assertThat(before.get("conflict")).isEqualTo(1L);
        String lock = RedisKeyConstants.TASK_A2A_RECONCILE_LOCK_PREFIX + taskId;
        waitForNoLock(lock, 5);
        String bearer = login();
        long auditAfter = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM audit_log", Long.class);

        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return result;
        }).when(client).query(anyString(), anyString(), anyBoolean());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var auto = workers.submit(() -> ReflectionTestUtils.invokeMethod(reconciliation, "reconcile", mapper.selectById(taskId)));
            try {
                assertThat(entered.await(8, TimeUnit.SECONDS)).isTrue();
                Object owner = redis.get(lock);
                assertThat(owner != null).isTrue();
                assertThat(manual(taskId, bearer).path("code").asInt()).isEqualTo(40900);
                assertThat(owner.equals(redis.get(lock))).isTrue();
            } finally { release.countDown(); }
            auto.get(8, TimeUnit.SECONDS);
            assertThat(fingerprint(taskId)).isEqualTo(before);
            assertThat(redis.hasKey(lock)).isFalse();

            reset(client);
            CountDownLatch oldEntered = new CountDownLatch(1), nextEntered = new CountDownLatch(1);
            CountDownLatch releaseOld = new CountDownLatch(1), releaseNext = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            doAnswer(invocation -> {
                Object result = invocation.callRealMethod();
                if (calls.incrementAndGet() == 1) {
                    oldEntered.countDown();
                    assertThat(releaseOld.await(70, TimeUnit.SECONDS)).isTrue();
                } else {
                    nextEntered.countDown();
                    assertThat(releaseNext.await(15, TimeUnit.SECONDS)).isTrue();
                }
                return result;
            }).when(client).query(anyString(), anyString(), anyBoolean());
            var old = workers.submit(() -> ReflectionTestUtils.invokeMethod(reconciliation, "reconcile", mapper.selectById(taskId)));
            try {
                assertThat(oldEntered.await(8, TimeUnit.SECONDS)).isTrue();
                Object oldOwner = redis.get(lock);
                assertThat(oldOwner != null).isTrue();
                // 不调用 EXPIRE/DEL；生产 55 秒租约自然到期。
                waitForNoLock(lock, 60);
                var successor = workers.submit(() -> manual(taskId, bearer));
                assertThat(nextEntered.await(8, TimeUnit.SECONDS)).isTrue();
                Object nextOwner = redis.get(lock);
                assertThat(nextOwner != null && !nextOwner.equals(oldOwner)).isTrue();
                releaseOld.countDown();
                old.get(8, TimeUnit.SECONDS);
                assertThat(nextOwner.equals(redis.get(lock))).isTrue();
                assertThat(fingerprint(taskId)).isEqualTo(before);
                releaseNext.countDown();
                var response = successor.get(8, TimeUnit.SECONDS);
                assertThat(response.path("code").asInt()).isZero();
                assertThat(response.path("data").path("reason").asText()).isEqualTo("DRAFT_VERSION_CONFLICT");
                assertThat(redis.hasKey(lock)).isFalse();
            } finally { releaseOld.countDown(); releaseNext.countDown(); }
        }
        assertThat(fingerprint(taskId)).isEqualTo(before);
        Integer lostOwner = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE id>? AND task_id=? AND action='TASK_RECOVERY_FINISHED' AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.trigger'))='AUTO' AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.reason'))='RECOVERY_CAPACITY_EXCEEDED'", Integer.class, auditAfter, taskId);
        Integer humanConflict = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE id>? AND task_id=? AND action='TASK_RECOVERY_FINISHED' AND actor_type=1 AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.trigger'))='MANUAL' AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.reason'))='DRAFT_VERSION_CONFLICT'", Integer.class, auditAfter, taskId);
        assertThat(lostOwner).isEqualTo(1);
        assertThat(humanConflict).isEqualTo(1);
        System.out.println("P5_DEPLOYMENT_PASS: real AUTO/MANUAL contention; natural 55s lease takeover; old owner rejected; successor lock preserved; original proof/draft/ledger unchanged; no new Task/model call");
    }

    private String login() throws Exception {
        Path file = Path.of(System.getenv("P5_RECOVERY_OWNER_FILE"));
        String requestBody = Files.readString(file);
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:9090/api/auth/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build(), HttpResponse.BodyHandlers.ofString());
        var value = JsonUtils.parse(response.body(), JsonNode.class);
        assertThat(response.statusCode() == 200 && value.path("code").asInt(-1) == 0).isTrue();
        String token = value.path("data").path("accessToken").asText();
        assertThat(!token.isBlank()).isTrue();
        return "Bearer " + token;
    }

    private JsonNode manual(long taskId, String bearer) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/task/tasks/" + taskId + "/recovery"))
                .timeout(Duration.ofSeconds(25)).header("Authorization", bearer)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        return JsonUtils.parse(response.body(), JsonNode.class);
    }

    private void waitForNoLock(String key, int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        while (redis.hasKey(key) && System.nanoTime() < deadline) { Thread.sleep(50); }
        assertThat(redis.hasKey(key)).isFalse();
    }

    private Map<String, Object> fingerprint(long taskId) {
        return jdbc.queryForMap("""
                SELECT t.status, SHA2(t.capability_token,256) AS proof,
                  SHA2(CONCAT_WS('|',t.space_id,t.agent_id,t.document_id,t.execution_mode,
                    t.document_version_snapshot,t.document_content_sha256,t.input_snapshot_schema_version,
                    t.input_snapshot_hash,COALESCE(t.derivation_request_hash,''),t.a2a_task_id,t.a2a_context_id),256) AS identity_hash,
                  d.version, SHA2(d.content,256) AS content_hash,
                  d.agent_staged_task_id, d.agent_staged_base_version, SHA2(d.agent_staged_content,256) AS staged_hash,
                  CAST(d.agent_staged_task_id=t.id AND d.version<>d.agent_staged_base_version AS SIGNED) AS conflict,
                  (SELECT COUNT(*) FROM token_usage_detail u WHERE u.execution_id=t.agent_execution_id) AS ledger_count,
                  (SELECT COUNT(*) FROM document_version v WHERE v.source_task_id=t.id) AS source_versions,
                  (SELECT COUNT(*) FROM execution_artifact a WHERE a.task_id=t.id) AS artifacts
                FROM task t JOIN document d ON d.id=t.document_id WHERE t.id=?
                """, taskId);
    }
}
