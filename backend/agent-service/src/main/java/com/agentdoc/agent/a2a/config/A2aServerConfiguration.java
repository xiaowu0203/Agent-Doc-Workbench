package com.agentdoc.agent.a2a.config;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A2A服务端Spring装配配置类
 * <p>
 * 负责A2A协议服务核心组件Bean注册；
 * 使用Java21虚拟线程执行任务运算与事件处理；
 * 组装事件总线、队列管理器、推送发送器、事件处理器、请求处理器等核心实例；
 * destroyMethod = "close"：容器销毁时自动关闭虚拟线程执行器，释放资源。
 * </p>
 */
@Configuration
public class A2aServerConfiguration {

    /**
     * A2A任务执行虚拟线程池
     * <p>用于Agent任务业务运算、工具调用、模型推理等任务执行；每任务一个虚拟线程。</p>
     *
     * @return ExecutorService 虚拟线程执行器，容器关闭自动close
     */
    @Bean(destroyMethod = "close")
    public ExecutorService a2aExecutionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * A2A事件处理虚拟线程池
     * <p>用于事件总线消费、事件分发、推送回调、任务状态变更事件处理。</p>
     *
     * @return ExecutorService 虚拟线程执行器，容器关闭自动close
     */
    @Bean(destroyMethod = "close")
    public ExecutorService a2aEventExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * A2A主事件总线
     * <p>任务状态、事件变更统一发布到此总线，供Processor消费处理。</p>
     *
     * @return MainEventBus 内存事件总线实例
     */
    @Bean
    public MainEventBus a2aMainEventBus() {
        return new MainEventBus();
    }

    /**
     * 内存版任务队列管理器
     * <p>管理任务排队、调度；依赖TaskStore读取任务状态，接收EventBus事件通知。</p>
     *
     * @param taskStore 任务存储，同时实现TaskStateProvider状态查询接口
     * @param eventBus 主事件总线
     * @return QueueManager 内存队列管理器
     */
    @Bean
    public QueueManager a2aQueueManager(TaskStore taskStore, MainEventBus eventBus) {
        return new InMemoryQueueManager((TaskStateProvider) taskStore, eventBus);
    }

    /**
     * 推送通知发送器
     * <p>负责读取推送配置，向外部地址发起任务变更回调HTTP推送。</p>
     *
     * @param configStore 推送配置存储
     * @return PushNotificationSender 基础推送发送实现
     */
    @Bean
    public PushNotificationSender a2aPushNotificationSender(PushNotificationConfigStore configStore) {
        return new BasePushNotificationSender(configStore);
    }

    /**
     * 事件总线处理器
     * <p>订阅MainEventBus，消费任务事件，驱动队列调度、触发外部推送回调；启动时调用ensureStarted完成初始化。</p>
     *
     * @param eventBus 主事件总线
     * @param taskStore 任务存储
     * @param sender 推送通知发送器
     * @param queueManager 任务队列管理器
     * @param executor 事件处理虚拟线程池 a2aEventExecutor
     * @return MainEventBusProcessor 事件处理器实例
     */
    @Bean
    public MainEventBusProcessor a2aMainEventBusProcessor(MainEventBus eventBus, TaskStore taskStore,
                                                          PushNotificationSender sender,
                                                          QueueManager queueManager,
                                                          @Qualifier("a2aEventExecutor") Executor executor) {
        MainEventBusProcessor processor = new MainEventBusProcessor(eventBus, taskStore, sender, queueManager);
        processor.setPushNotificationExecutor(executor);
        processor.ensureStarted();
        return processor;
    }

    /**
     * A2A协议请求处理器
     * <p>
     * 组装DefaultRequestHandler核心委托实现，再包一层SpringA2aRequestHandler做Spring环境适配；
     * executionExecutor：业务任务执行线程；eventExecutor：事件消费线程。
     * </p>
     *
     * @param agentExecutor Agent执行器，驱动Agent逻辑与MCP工具调用
     * @param taskStore 任务存储
     * @param queueManager 任务队列管理器
     * @param configStore 推送配置存储
     * @param processor 事件总线处理器
     * @param executionExecutor 任务执行虚拟线程池 a2aExecutionExecutor
     * @param eventExecutor 事件消费虚拟线程池 a2aEventExecutor
     * @return RequestHandler A2A请求处理入口
     */
    @Bean
    public RequestHandler a2aRequestHandler(AgentExecutor agentExecutor, TaskStore taskStore,
                                            QueueManager queueManager,
                                            PushNotificationConfigStore configStore,
                                            MainEventBusProcessor processor,
                                            @Qualifier("a2aExecutionExecutor") Executor executionExecutor,
                                            @Qualifier("a2aEventExecutor") Executor eventExecutor) {
        RequestHandler delegate = DefaultRequestHandler.builder()
                /**
                 * A2A 业务执行器：（当前实现是WorkbenchAgentExecutor）
                 * 真正执行业务 Agent 任务，以及处理取消请求。
                 * 内部最终会调用 AgentExecutor.execute(...) / cancel(...)
                 */
                .agentExecutor(agentExecutor)
                /**
                 * A2A Task 持久化存储：（当前实现是MySqlA2aTaskStore）
                 * 保存、查询、更新完整的 A2A Task。
                 */
                .taskStore(taskStore)
                /**
                 * 任务队列管理器：（当前实现是InMemoryQueueManager）
                 * 管理 A2A Task 的排队、调度和活跃状态。
                 */
                .queueManager(queueManager)
                /**
                 * 推送回调配置存储：（当前实现是 MySqlA2aPushConfigStore）
                 * 保存每个 A2A Task 的 TaskPushNotificationConfig
                 * 包括 task-service 的 callback URL、通知 Token 等
                 */
                .pushConfigStore(configStore)
                /**
                 * 主事件总线处理器：
                 * 消费 AgentEmitter 发布的任务事件
                 * 更新 TaskStore，并根据 pushConfigStore 中的配置
                 * 触发外部 HTTP 回调（例如 startWork、addArtifact、complete、fail、cancel）
                 */
                .mainEventBusProcessor(processor)
                /**
                 * Agent 业务执行线程池：
                 * 用于异步执行 agentExecutor.execute(...)
                 * 避免 /a2a/message:send 请求线程直接阻塞整个 Agent 执行过程
                 */
                .executor(executionExecutor)
                /**
                 * 事件消费线程池：
                 * 用于异步消费任务事件、更新 TaskStore、
                 * 调用 PushNotificationSender 向 task-service 推送回调。
                 */
                .eventConsumerExecutor(eventExecutor)

                /**
                 * 构建 A2A SDK 默认请求处理器。
                 * 它会统一处理 send、get task、list task、cancel、
                 * push notification config 等标准 A2A 接口。
                 */
                .build();
        return new SpringA2aRequestHandler(delegate);
    }
}
