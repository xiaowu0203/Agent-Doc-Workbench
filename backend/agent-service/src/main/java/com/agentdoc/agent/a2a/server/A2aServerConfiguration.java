package com.agentdoc.agent.a2a.server;

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
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .pushConfigStore(configStore)
                .mainEventBusProcessor(processor)
                .executor(executionExecutor)
                .eventConsumerExecutor(eventExecutor)
                .build();
        return new SpringA2aRequestHandler(delegate);
    }
}
