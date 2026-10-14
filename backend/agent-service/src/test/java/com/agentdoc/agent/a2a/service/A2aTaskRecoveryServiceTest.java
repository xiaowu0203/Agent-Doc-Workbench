package com.agentdoc.agent.a2a.service;

import com.agentdoc.agent.a2a.config.A2aJacksonConfig;
import com.agentdoc.agent.a2a.store.A2aStorePayloadCodec;
import com.agentdoc.agent.a2a.store.MySqlA2aTaskStore;
import com.agentdoc.agent.a2a.executor.WorkbenchAgentExecutor;
import com.agentdoc.agent.execution.application.AgentExecutionApplicationService;
import com.agentdoc.agent.mapper.A2aTaskStoreMapper;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.A2aTaskStoreEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.service.AgentExecutionQueryService;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.feign.dto.TaskRecoveryIdentityDTO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.security.TaskRecoveryVerifier;
import com.agentdoc.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.mybatis.spring.SqlSessionTemplate;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskManager;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class A2aTaskRecoveryServiceTest {
    private final AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
    private final TaskStore store = mock(TaskStore.class);
    private final RequestHandler handler = mock(RequestHandler.class);
    private final AgentExecutionQueryService query = mock(AgentExecutionQueryService.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final SecurityVerifyProperties properties = new SecurityVerifyProperties();
    private final TaskRecoveryVerifier verifier = new TaskRecoveryVerifier(decoder, properties);
    private final A2aTaskRecoveryService service = new A2aTaskRecoveryService(verifier, executions, store, handler, query);
    private final TaskRecoveryIdentityDTO identity = new TaskRecoveryIdentityDTO(1L, 2L, 3L, 4L, "LIVE", 1L, "a".repeat(64), 1, "b".repeat(64), null, null);
    private AgentExecutionEntity execution;

    @BeforeEach
    void setup() {
        properties.setTaskRecoveryEnabled(true);
        when(decoder.decode("recovery")).thenReturn(capability(false));
        execution = new AgentExecutionEntity();
        execution.setId(5L);
        execution.setWorkbenchTaskId(1L);
        execution.setAgentId(2L);
        execution.setSpaceId(3L);
        execution.setA2aTaskId("remote");
        execution.setA2aContextId("context");
        when(executions.selectList(any())).thenReturn(List.of(execution));
        when(store.get("remote")).thenReturn(remote(TaskState.TASK_STATE_COMPLETED));
        when(query.getTokenUsageByWorkbenchTask(1L)).thenReturn(new AgentExecutionTokenUsageVO(5L, 6L, 1L,
                BigDecimal.ZERO, BigDecimal.ZERO, "CNY", 1, LocalDateTime.now(), 10L, false, 0L, false, 20L, false));
    }

    private OnlineDispatchIdentityDTO onlineIdentity;

    @Test
    void onlineRecoveryMatchesPersistedGenerationAndCannotStripHistoricalIdentity() throws A2AError {
        onlineIdentity = new OnlineDispatchIdentityDTO("11", "61", 2, "c".repeat(64), 3L, "d".repeat(64));
        execution.setOnlineExperimentId(11L); execution.setOnlineAssignmentId(61L); execution.setOnlineBindingSchemaVersion(2);
        execution.setOnlineBindingHash(onlineIdentity.bindingHash()); execution.setOnlineSlotGeneration(3L); execution.setOnlineSlotPermitHash(onlineIdentity.permitHash());
        when(store.get("remote")).thenReturn(remote(TaskState.TASK_STATE_COMPLETED)); when(decoder.decode("recovery")).thenReturn(capability(false));
        assertThat(service.recover("remote", "recovery", false).tokenUsage().executionId()).isEqualTo(5L);
        execution.setOnlineSlotGeneration(4L);
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        execution.setOnlineSlotGeneration(3L); onlineIdentity = null;
        when(store.get("remote")).thenReturn(remote(TaskState.TASK_STATE_COMPLETED));
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        verifyNoInteractions(handler);
    }

    @Test
    void checksExistingExecutionAndFrozenInputAndDoesNotExposeOriginalProofHistory() throws A2AError {
        var result = service.recover("remote", "recovery", false);
        assertThat(result.remoteTask().id()).isEqualTo("remote");
        assertThat(result.remoteTask().history()).isEmpty();
        assertThat(result.tokenUsage().executionId()).isEqualTo(5L);
        verifyNoInteractions(handler);
    }

    @Test
    void missingOrAmbiguousExecutionAndMissingInputFailClosed() {
        when(executions.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        when(executions.selectList(any())).thenReturn(List.of(execution, execution));
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        when(executions.selectList(any())).thenReturn(List.of(execution));
        when(store.get("remote")).thenReturn(Task.builder(remote(TaskState.TASK_STATE_COMPLETED)).history(List.of()).build());
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        verifyNoInteractions(handler, query);
    }

    @Test
    void executionSpaceContextAndOriginalProofJtiCannotBeSubstituted() {
        execution.setSpaceId(99L);
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        execution.setSpaceId(3L);
        execution.setA2aContextId("other");
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        execution.setA2aContextId("context");
        Jwt changed = Jwt.withTokenValue("recovery").headers(headers -> headers.putAll(capability(false).getHeaders()))
                .claims(claims -> { claims.putAll(capability(false).getClaims()); claims.put(TaskRecoveryConstant.SOURCE_JTI, "other"); }).build();
        when(decoder.decode("recovery")).thenReturn(changed);
        assertThatThrownBy(() -> service.recover("remote", "recovery", false)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        verifyNoInteractions(handler, query);
    }

    @Test
    void everyFrozenFieldIsValidatedAgainstOriginalA2aInput() {
        identity.toClaims().forEach((field, value) -> {
            Jwt changed = Jwt.withTokenValue("recovery").headers(headers -> headers.putAll(capability(false).getHeaders()))
                    .claims(claims -> { claims.putAll(capability(false).getClaims()); claims.put(field, value == null ? "c".repeat(64) : "different"); }).build();
            when(decoder.decode("recovery")).thenReturn(changed);
            assertThatThrownBy(() -> service.recover("remote", "recovery", false)).isInstanceOf(JwtException.class);
        });
        verifyNoInteractions(handler, query);
    }

    @Test
    void cancelNeedsItsOwnActionAndAlreadyFinalRemoteDoesNotInvokeHandler() throws A2AError {
        assertThatThrownBy(() -> service.recover("remote", "recovery", true)).isInstanceOf(JwtException.class);
        when(decoder.decode("recovery")).thenReturn(capability(true));
        service.recover("remote", "recovery", true);
        verifyNoInteractions(handler);
        when(store.get("remote")).thenReturn(remote(TaskState.TASK_STATE_WORKING));
        when(handler.onCancelTask(any(), any())).thenReturn(remote(TaskState.TASK_STATE_CANCELED));
        assertThat(service.recover("remote", "recovery", true).remoteTask().status().state()).isEqualTo(TaskState.TASK_STATE_CANCELED);
        verify(handler, times(1)).onCancelTask(any(), any());
    }

    @Test
    void sdkTaskManagerPersistsInitialInputThroughRealEncryptedPayloadCodec() throws A2AServerException, A2AError {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new A2aJacksonConfig().a2aPartJacksonCustomizer().customize(builder);
        A2aStorePayloadCodec codec = new A2aStorePayloadCodec(builder.build(),
                new AgentConfigCryptoService(Base64.getEncoder().encodeToString(key)));
        A2aTaskStoreMapper mapper = mock(A2aTaskStoreMapper.class);
        AtomicReference<A2aTaskStoreEntity> stored = new AtomicReference<>();
        doAnswer(call -> { stored.set(call.getArgument(0)); return 1; }).when(mapper).upsert(any());
        when(mapper.selectById("remote")).thenAnswer(call -> stored.get());
        MySqlA2aTaskStore mysql = new MySqlA2aTaskStore(mapper, codec);
        TaskManager manager = new TaskManager("remote", "context", mysql, initialMessage());
        manager.process(TaskStatusUpdateEvent.builder().taskId("remote").contextId("context")
                .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_COMPLETED)).build(), true);
        assertThat(stored.get().getEncryptedPayload()).doesNotContain(originalProof(), "baseline", "workbenchTaskId");
        Task recovered = mysql.get("remote");
        assertThat(recovered.history()).hasSize(1);
        when(store.get("remote")).thenReturn(recovered);
        assertThat(service.recover("remote", "recovery", false).tokenUsage().executionId()).isEqualTo(5L);
    }

    /** 真实 SDK、RSA 验签、生产 Mapper/XML 与 H2 持久化；执行档案/模型执行仍为替身。 */
    @Test
    void realAsyncSdkRunningTaskCanBeQueriedAndCanceledThroughRecoveryWithoutRewritingStoredHistory() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new A2aJacksonConfig().a2aPartJacksonCustomizer().customize(builder);
        A2aStorePayloadCodec codec = new A2aStorePayloadCodec(builder.build(),
                new AgentConfigCryptoService(Base64.getEncoder().encodeToString(key)));
        DriverManagerDataSource database = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE a2a_task_store(task_id VARCHAR(64) PRIMARY KEY, context_id VARCHAR(64), state VARCHAR(64), status_timestamp TIMESTAMP, encrypted_payload CLOB, updated_at TIMESTAMP)");
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(database);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(A2aTaskStoreMapper.class);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/A2aTaskStoreMapper.xml"));
        A2aTaskStoreMapper mapper = new SqlSessionTemplate(factory.getObject()).getMapper(A2aTaskStoreMapper.class);
        MySqlA2aTaskStore mysql = new MySqlA2aTaskStore(mapper, codec);
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(mysql, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, mysql, mock(PushNotificationSender.class), queues);
        ReflectionTestUtils.invokeMethod(processor, "start");
        CountDownLatch canceled = new CountDownLatch(1);
        AgentExecutionApplicationService application = mock(AgentExecutionApplicationService.class);
        doAnswer(call -> {
            AgentEmitter emitter = call.getArgument(1);
            emitter.startWork();
            assertThat(canceled.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(application).execute(any(), any());
        doAnswer(call -> {
            AgentEmitter emitter = call.getArgument(1);
            emitter.cancel();
            canceled.countDown();
            return null;
        }).when(application).cancel(any(), any());
        try (var executionsPool = Executors.newVirtualThreadPerTaskExecutor();
             var eventsPool = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultRequestHandler sdk = DefaultRequestHandler.builder().agentExecutor(new WorkbenchAgentExecutor(application))
                    .taskStore(mysql).queueManager(queues).pushConfigStore(new InMemoryPushNotificationConfigStore())
                    .mainEventBusProcessor(processor).executor(executionsPool).eventConsumerExecutor(eventsPool).build();
            Task submitted = (Task) sdk.onMessageSend(MessageSendParams.builder().message(initialMessage())
                            .configuration(MessageSendConfiguration.builder().returnImmediately(true).build()).build(),
                    new ServerCallContext(null, Map.of(), Set.of(), null));
            execution.setA2aTaskId(submitted.id());
            execution.setA2aContextId(submitted.contextId());
            Jwt permitted = Jwt.withTokenValue("sdk-recovery").headers(headers -> headers.putAll(capability(true).getHeaders()))
                    .claims(claims -> {
                        claims.putAll(capability(true).getClaims());
                        claims.put(TaskRecoveryConstant.A2A_TASK_ID, submitted.id());
                    }).build();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).build();
            NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
            NimbusJwtDecoder signedDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
            String signed = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                    JwtClaimsSet.builder().claims(claims -> claims.putAll(permitted.getClaims())).build())).getTokenValue();
            A2aTaskRecoveryService sdkRecovery = new A2aTaskRecoveryService(
                    new TaskRecoveryVerifier(signedDecoder, properties), this.executions, mysql, sdk, query);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            Task working;
            do {
                working = mysql.get(submitted.id());
                if (working != null && working.status().state() == TaskState.TASK_STATE_WORKING) { break; }
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertThat(working.status().state()).isEqualTo(TaskState.TASK_STATE_WORKING);
            String initialHistory = JsonUtils.toJson(working.history());
            String[] parts = signed.split("\\.");
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            signature[0] ^= 1;
            String corrupted = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            assertThatThrownBy(() -> sdkRecovery.recover(submitted.id(), corrupted, true)).isInstanceOf(JwtException.class);
            String queryOnly = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                    JwtClaimsSet.builder().claims(claims -> {
                        claims.putAll(permitted.getClaims());
                        claims.put(TaskRecoveryConstant.ACTIONS, List.of(TaskRecoveryConstant.QUERY));
                    }).build())).getTokenValue();
            assertThatThrownBy(() -> sdkRecovery.recover(submitted.id(), queryOnly, true)).isInstanceOf(JwtException.class);
            assertThat(mysql.get(submitted.id()).status().state()).isEqualTo(TaskState.TASK_STATE_WORKING);
            var active = sdkRecovery.recover(submitted.id(), signed, false);
            assertThat(active.remoteTask().status().state()).isEqualTo(TaskState.TASK_STATE_WORKING);
            assertThat(active.remoteTask().history()).isEmpty();
            var terminal = sdkRecovery.recover(submitted.id(), signed, true);
            assertThat(terminal.remoteTask().status().state()).isEqualTo(TaskState.TASK_STATE_CANCELED);
            assertThat(terminal.remoteTask().history()).isEmpty();
            assertThat(JsonUtils.toJson(mysql.get(submitted.id()).history())).isEqualTo(initialHistory);
            sdkRecovery.recover(submitted.id(), signed, true);
            verify(application, times(1)).cancel(any(), any());
            verify(application, times(1)).execute(any(), any());
            assertThat(mapper.selectById(submitted.id()).getEncryptedPayload()).doesNotContain(originalProof(), "baseline");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM a2a_task_store", Integer.class)).isEqualTo(1);
        } finally {
            canceled.countDown();
            ReflectionTestUtils.invokeMethod(processor, "stop");
            jdbc.execute("SHUTDOWN");
        }
    }

    private Task remote(TaskState state) {
        return Task.builder().id("remote").contextId("context").status(new org.a2aproject.sdk.spec.TaskStatus(state))
                .history(List.of(initialMessage())).build();
    }

    private Message initialMessage() {
        AgentTaskInputDTO input = new AgentTaskInputDTO(1L, 2L, 3L, 4L, 1000L, "LIVE", 1L, "a".repeat(64), 1,
                "b".repeat(64), null, null, null, null, null, null, null, null, "http://localhost:8083/mcp", originalProof(), onlineIdentity);
        return Message.builder().messageId("input").role(Message.Role.ROLE_USER).parts(new TextPart("baseline"), new DataPart(input)).build();
    }

    private String originalProof() {
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"jti\":\"source\"}".getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private Jwt capability(boolean cancel) {
        return Jwt.withTokenValue("recovery").header("alg", "RS256").issuer("agent-doc-workbench")
                .subject("task-service").issuedAt(Instant.now().minusSeconds(10)).notBefore(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(100)).audience(List.of(TaskRecoveryConstant.A2A_AUDIENCE))
                .claims(claims -> {
                    var frozen = new TaskRecoveryIdentityDTO(identity.taskId(), identity.agentId(), identity.spaceId(), identity.documentId(), identity.executionMode(),
                            identity.documentVersionSnapshot(), identity.documentContentSha256(), identity.inputSnapshotSchemaVersion(), identity.inputSnapshotHash(), identity.derivationRequestHash(), onlineIdentity);
                    frozen.toClaims().forEach((field, value) -> { if (value != null) { claims.put(field, value); } });
                    claims.put(JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.ACTOR_SERVICE);
                    claims.put(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_SERVICE);
                    claims.put(JwtConstant.CLAIM_SERVICE, TaskRecoveryConstant.SERVICE);
                    claims.put(TaskRecoveryConstant.PURPOSE, TaskRecoveryConstant.A2A_PURPOSE);
                    claims.put(TaskRecoveryConstant.ACTIONS, cancel ? List.of(TaskRecoveryConstant.QUERY, TaskRecoveryConstant.CANCEL) : List.of(TaskRecoveryConstant.QUERY));
                    claims.put(TaskRecoveryConstant.A2A_TASK_ID, "remote");
                    claims.put(TaskRecoveryConstant.RECOVERY_ID, UUID.randomUUID().toString());
                    claims.put(TaskRecoveryConstant.SOURCE_JTI, "source");
                    claims.put("jti", "recovery-token");
                }).build();
    }
}
