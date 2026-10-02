package com.agentdoc.agent.a2a.executor;

import com.agentdoc.agent.a2a.config.A2aJacksonConfig;
import com.agentdoc.agent.a2a.store.A2aStorePayloadCodec;
import com.agentdoc.agent.a2a.store.MySqlA2aTaskStore;
import com.agentdoc.agent.execution.application.AgentExecutionApplicationService;
import com.agentdoc.agent.mapper.A2aTaskStoreMapper;
import com.agentdoc.agent.pojo.entity.A2aTaskStoreEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.common.utils.JsonUtils;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.MainEventBusProcessorCallback;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 真实异步 SDK/事件总线/加密 codec 链路；Mapper 用内存替身，不调用模型或线上数据库。 */
class WorkbenchAgentExecutorAsyncTest {

    @Test
    void asynchronousSendRetainsInitialInputAfterAllStatusAndArtifactEvents() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new A2aJacksonConfig().a2aPartJacksonCustomizer().customize(builder);
        A2aStorePayloadCodec codec = new A2aStorePayloadCodec(builder.build(),
                new AgentConfigCryptoService(Base64.getEncoder().encodeToString(key)));
        A2aTaskStoreMapper mapper = mock(A2aTaskStoreMapper.class);
        Map<String, A2aTaskStoreEntity> rows = new ConcurrentHashMap<>();
        doAnswer(call -> {
            A2aTaskStoreEntity row = call.getArgument(0);
            rows.put(row.getTaskId(), row);
            return 1;
        }).when(mapper).upsert(any());
        when(mapper.selectById(anyString())).thenAnswer(call -> rows.get((String) call.getArgument(0)));
        MySqlA2aTaskStore store = new MySqlA2aTaskStore(mapper, codec);
        MainEventBus bus = new MainEventBus();
        InMemoryQueueManager queues = new InMemoryQueueManager(store, bus);
        MainEventBusProcessor processor = new MainEventBusProcessor(bus, store,
                mock(PushNotificationSender.class), queues);
        CountDownLatch finalized = new CountDownLatch(1);
        MainEventBusProcessorCallback callback = mock(MainEventBusProcessorCallback.class);
        doAnswer(call -> { finalized.countDown(); return null; }).when(callback).onTaskFinalized(any());
        processor.setCallback(callback);
        // 直接组装没有 Spring/CDI 的 @PostConstruct，显式启动与停止测试自己的事件线程。
        ReflectionTestUtils.invokeMethod(processor, "start");
        AgentExecutionApplicationService application = mock(AgentExecutionApplicationService.class);
        doAnswer(call -> {
            AgentEmitter emitter = call.getArgument(1);
            emitter.startWork();
            emitter.addArtifact(List.of(new TextPart("test-result")));
            emitter.complete();
            return null;
        }).when(application).execute(any(), any());
        Message initial = Message.builder().messageId("test-initial").role(Message.Role.ROLE_USER)
                .parts(new TextPart("test-instruction"), new DataPart(Map.of("workbenchTaskId", 1L,
                        "taskCapability", "test-original-proof"))).build();
        try (var executions = Executors.newVirtualThreadPerTaskExecutor();
             var events = Executors.newVirtualThreadPerTaskExecutor()) {
            DefaultRequestHandler handler = DefaultRequestHandler.builder()
                    .agentExecutor(new WorkbenchAgentExecutor(application)).taskStore(store).queueManager(queues)
                    .pushConfigStore(new InMemoryPushNotificationConfigStore()).mainEventBusProcessor(processor)
                    .executor(executions).eventConsumerExecutor(events).build();
            Task submitted = (Task) handler.onMessageSend(MessageSendParams.builder().message(initial)
                            .configuration(MessageSendConfiguration.builder().returnImmediately(true).build()).build(),
                    new ServerCallContext(null, Map.of(), Set.of(), null));
            assertThat(finalized.await(5, TimeUnit.SECONDS)).isTrue();
            Task restored = store.get(submitted.id());
            assertThat(restored.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
            assertThat(restored.artifacts()).hasSize(1);
            assertThat(rows.get(submitted.id()).getEncryptedPayload())
                    .doesNotContain("test-original-proof", "test-instruction", "workbenchTaskId");
            assertThat(restored.history()).hasSize(1);
            Message saved = restored.history().getFirst();
            // SDK 补齐协议身份，业务输入与原证明必须保持原样。
            assertThat(saved.taskId()).isEqualTo(submitted.id());
            assertThat(saved.contextId()).isEqualTo(submitted.contextId());
            assertThat(saved.messageId()).isEqualTo(initial.messageId());
            assertThat(saved.role()).isEqualTo(initial.role());
            assertThat(JsonUtils.toJson(saved.parts())).isEqualTo(JsonUtils.toJson(initial.parts()));
            assertThat(saved.metadata()).isEqualTo(initial.metadata());
            assertThat(saved.extensions()).isEqualTo(initial.extensions());
            assertThat(saved.referenceTaskIds()).isEqualTo(initial.referenceTaskIds());
        } finally {
            ReflectionTestUtils.invokeMethod(processor, "stop");
        }
    }
}
