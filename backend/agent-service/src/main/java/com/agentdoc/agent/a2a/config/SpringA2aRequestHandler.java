package com.agentdoc.agent.a2a.config;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;

import java.util.concurrent.Flow;

/**
 * Spring环境适配包装实现，装饰器模式实现 {@link RequestHandler}
 * <p>
 * 对底层A2A核心协议处理器{@code delegate}做一层包装转发；
 * 当前版本仅做全部方法透传，预留切面扩展点，用于接入Spring体系能力：
 * 如异常转换、MDC链路追踪、日志埋点、监控指标采集、上下文透传、权限增强等。
 * </p>
 * <p>装饰器模式：核心A2A协议逻辑放在{@code DefaultRequestHandler}，与Spring框架解耦；
 * 本包装类负责桥接Spring环境，不修改业务逻辑。</p>
 */
final class SpringA2aRequestHandler implements RequestHandler {

    /** 被委托的底层A2A协议请求处理器，实际执行业务逻辑 */
    private final RequestHandler delegate;

    /**
     * @param delegate 底层真实RequestHandler实现（DefaultRequestHandler）
     */
    SpringA2aRequestHandler(RequestHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * 查询单个任务详情
     * @param params 查询参数（a2a任务id、历史消息条数）
     * @param context A2A调用上下文
     * @return 任务完整实体
     * @throws A2AError A2A协议异常
     */
    @Override
    public Task onGetTask(TaskQueryParams params, ServerCallContext context) throws A2AError {
        return delegate.onGetTask(params, context);
    }

    /**
     * 分页查询任务列表
     * @param params 列表查询参数：contextId、状态过滤、分页游标等
     * @param context A2A调用上下文
     * @return 分页任务列表结果
     * @throws A2AError A2A协议异常
     */
    @Override
    public ListTasksResult onListTasks(ListTasksParams params, ServerCallContext context) throws A2AError {
        return delegate.onListTasks(params, context);
    }

    /**
     * 取消正在运行的任务
     * @param params 待取消任务id参数
     * @param context A2A调用上下文
     * @return 更新后任务实体
     * @throws A2AError A2A协议异常
     */
    @Override
    public Task onCancelTask(CancelTaskParams params, ServerCallContext context) throws A2AError {
        return delegate.onCancelTask(params, context);
    }

    /**
     * 同步发送A2A消息（非流式）
     * @param params 消息发送入参
     * @param context A2A调用上下文
     * @return 事件响应
     * @throws A2AError A2A协议异常
     */
    @Override
    public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) throws A2AError {
        return delegate.onMessageSend(params, context);
    }

    /**
     * 流式发送A2A消息，返回SSE事件流
     * @param params 消息发送入参
     * @param context A2A调用上下文
     * @return 流式事件Publisher
     * @throws A2AError A2A协议异常
     */
    @Override
    public Flow.Publisher<StreamingEventKind> onMessageSendStream(MessageSendParams params,
                                                                  ServerCallContext context) throws A2AError {
        return delegate.onMessageSendStream(params, context);
    }

    /**
     * 创建任务推送回调通知配置
     * @param config 推送配置
     * @param context A2A调用上下文
     * @return 已保存推送配置
     * @throws A2AError A2A协议异常
     */
    @Override
    public TaskPushNotificationConfig onCreateTaskPushNotificationConfig(TaskPushNotificationConfig config,
                                                                          ServerCallContext context) throws A2AError {
        return delegate.onCreateTaskPushNotificationConfig(config, context);
    }

    /**
     * 查询单条推送回调配置
     * @param params 查询参数（taskId + configId）
     * @param context A2A调用上下文
     * @return 推送配置详情
     * @throws A2AError A2A协议异常
     */
    @Override
    public TaskPushNotificationConfig onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigParams params,
                                                                       ServerCallContext context) throws A2AError {
        return delegate.onGetTaskPushNotificationConfig(params, context);
    }

    /**
     * SSE订阅任务实时事件
     * @param params 任务id参数
     * @param context A2A调用上下文
     * @return SSE流式事件Publisher
     * @throws A2AError A2A协议异常
     */
    @Override
    public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params,
                                                                ServerCallContext context) throws A2AError {
        return delegate.onSubscribeToTask(params, context);
    }

    /**
     * 分页查询某个任务下全部推送回调配置
     * @param params 列表查询参数
     * @param context A2A调用上下文
     * @return 推送配置分页结果
     * @throws A2AError A2A协议异常
     */
    @Override
    public ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(
            ListTaskPushNotificationConfigsParams params, ServerCallContext context) throws A2AError {
        return delegate.onListTaskPushNotificationConfigs(params, context);
    }

    /**
     * 删除任务推送回调配置
     * @param params 删除参数（taskId + configId）
     * @param context A2A调用上下文
     * @throws A2AError A2A协议异常
     */
    @Override
    public void onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigParams params,
                                                    ServerCallContext context) throws A2AError {
        delegate.onDeleteTaskPushNotificationConfig(params, context);
    }

    /**
     * 任务访问鉴权校验
     * @param taskId a2a任务id
     * @param context A2A调用上下文
     * @param operation 当前要执行的任务操作类型
     * @throws A2AError 无访问权限抛出A2AError
     */
    @Override
    public void authorizeTaskAccess(String taskId, ServerCallContext context,
                                    TaskOperation operation) throws A2AError {
        delegate.authorizeTaskAccess(taskId, context, operation);
    }
}
