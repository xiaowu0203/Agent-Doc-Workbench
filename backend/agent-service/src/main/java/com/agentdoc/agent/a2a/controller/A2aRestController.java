package com.agentdoc.agent.a2a.controller;

import com.agentdoc.common.constant.A2aMetadataConstant;
import com.agentdoc.agent.a2a.service.A2aRequestAuthorizationService;
import com.agentdoc.agent.a2a.service.AgentCardService;
import com.agentdoc.agent.pojo.param.A2aPushConfigSearchParam;
import com.agentdoc.agent.pojo.param.A2aTaskSearchParam;
import io.swagger.v3.oas.annotations.Operation;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
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
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;

/**
 * A2A 协议 HTTP 接入控制器
 * <p>
 * 实现A2A协议HTTP‑JSON接口，对外暴露Agent能力；
 * 所有业务接口入口统一在这里；鉴权前置调用{@link A2aRequestAuthorizationService}做Task‑Capability令牌作用域校验；
 * 协议业务逻辑全部委托给{@link RequestHandler}处理，Controller仅负责：协议头、请求报文接收、鉴权、组装调用上下文、返回响应；
 * 支持普通JSON响应与SSE流式响应（TEXT_EVENT_STREAM）。
 * </p>
 * <p>
 * 协议约定：
 * <ul>
 * <li>请求/响应Content‑Type：{@code application/a2a+json}</li>
 * <li>协议版本放在请求头：{@code A2A‑Version}</li>
 * <li>well‑known接口对外暴露Agent‑Card元信息</li>
 * </ul>
 * </p>
 */
@RestController
public class A2aRestController {

    /** A2A协议标准JSON媒体类型 */
    private static final String A2A_MEDIA_TYPE = "application/a2a+json";
    /** A2A协议版本请求头 */
    private static final String PROTOCOL_VERSION_HEADER = "A2A-Version";

    private final AgentCardService agentCardService;
    /** A2A请求能力令牌鉴权服务，每个业务接口入口必须前置调用 */
    private final A2aRequestAuthorizationService authorizationService;
    /** A2A协议请求处理器，所有协议业务逻辑实现层 */
    private final RequestHandler requestHandler;

    public A2aRestController(AgentCardService agentCardService,
                             A2aRequestAuthorizationService authorizationService,
                             RequestHandler requestHandler) {
        this.agentCardService = agentCardService;
        this.authorizationService = authorizationService;
        this.requestHandler = requestHandler;
    }

    @Operation(summary = "获取Agent Card元描述(用于客户端发现本Agent的能力、协议信息，无需A2A能力令牌鉴权)")
    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCard() {
        return agentCardService.get();
    }

    @Operation(summary = "A2A同步消息发送接口(提交Agent任务，同步返回事件结果；非流式)")
    @PostMapping(value = "/a2a/message:send", consumes = A2A_MEDIA_TYPE, produces = A2A_MEDIA_TYPE)
    public EventKind send(@RequestBody MessageSendParams params,
                          @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version)
            throws A2AError {
        authorizationService.requireTaskScope(params);
        return requestHandler.onMessageSend(params, callContext(version, false));
    }

    @Operation(summary = "A2A流式消息发送接口，SSE长连接(提交Agent任务，以SSE流式返回增量事件流)")
    @PostMapping(value = "/a2a/message:stream", consumes = A2A_MEDIA_TYPE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flow.Publisher<StreamingEventKind> sendStream(
            @RequestBody MessageSendParams params,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version) throws A2AError {
        authorizationService.requireTaskScope(params);
        return requestHandler.onMessageSendStream(params, callContext(version, true));
    }

    @Operation(summary = "查询单个A2A任务详情")
    @GetMapping(value = "/a2a/tasks/{id}", produces = A2A_MEDIA_TYPE)
    public Task get(@PathVariable String id,
                    @RequestParam(required = false) Integer historyLength,
                    @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version)
            throws A2AError {
        authorizationService.requireA2aTaskScope(id);
        return requestHandler.onGetTask(new TaskQueryParams(id, historyLength), callContext(version, false));
    }

    @Operation(summary = "按contextId列举任务列表，分页查询")
    @GetMapping(value = "/a2a/tasks", produces = A2A_MEDIA_TYPE)
    public ListTasksResult list(@ModelAttribute A2aTaskSearchParam searchParam,
                                @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version)
            throws A2AError {
        authorizationService.requireA2aContextScope(searchParam.contextId());
        ListTasksParams params = ListTasksParams.builder()
                .contextId(searchParam.contextId())
                .status(searchParam.status())
                .pageSize(searchParam.pageSize())
                .pageToken(searchParam.pageToken())
                .historyLength(searchParam.historyLength())
                .statusTimestampAfter(searchParam.statusTimestampAfter())
                .includeArtifacts(searchParam.includeArtifacts())
                .build();
        return requestHandler.onListTasks(params, callContext(version, false));
    }

    @Operation(summary = "取消正在执行的A2A任务")
    @PostMapping(value = "/a2a/tasks/{id}:cancel", produces = A2A_MEDIA_TYPE)
    public Task cancel(@PathVariable String id,
                       @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version)
            throws A2AError {
        authorizationService.requireA2aTaskScope(id);
        return requestHandler.onCancelTask(new CancelTaskParams(id), callContext(version, false));
    }

    @Operation(summary = "SSE订阅任务事件，长连接实时接收任务增量更新")
    @GetMapping(value = "/a2a/tasks/{id}:subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flow.Publisher<StreamingEventKind> subscribe(
            @PathVariable String id,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version) throws A2AError {
        authorizationService.requireA2aTaskScope(id);
        return requestHandler.onSubscribeToTask(new TaskIdParams(id), callContext(version, true));
    }

    @Operation(summary = "创建任务推送回调通知配置(配置后任务状态变更会主动回调外部推送地址)")
    @PostMapping(value = "/a2a/tasks/{taskId}/pushNotificationConfigs",
            consumes = A2A_MEDIA_TYPE, produces = A2A_MEDIA_TYPE)
    public TaskPushNotificationConfig createPushConfig(
            @PathVariable String taskId,
            @RequestBody TaskPushNotificationConfig config,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version) throws A2AError {
        authorizationService.requireA2aTaskScope(taskId);
        TaskPushNotificationConfig scopedConfig = new TaskPushNotificationConfig(
                config.id(), taskId, config.url(), config.token(), config.authentication(), config.tenant());
        return requestHandler.onCreateTaskPushNotificationConfig(scopedConfig, callContext(version, false));
    }

    @Operation(summary = "查询任务下全部推送回调配置，分页")
    @GetMapping(value = "/a2a/tasks/{taskId}/pushNotificationConfigs", produces = A2A_MEDIA_TYPE)
    public ListTaskPushNotificationConfigsResult listPushConfigs(
            @PathVariable String taskId,
            @ModelAttribute A2aPushConfigSearchParam searchParam,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version) throws A2AError {
        authorizationService.requireA2aTaskScope(taskId);
        ListTaskPushNotificationConfigsParams params = new ListTaskPushNotificationConfigsParams(
                taskId, searchParam.pageSize(), searchParam.pageToken(), null);
        return requestHandler.onListTaskPushNotificationConfigs(params, callContext(version, false));
    }

    @Operation(summary = "获取单条推送回调配置详情")
    @GetMapping(value = "/a2a/tasks/{taskId}/pushNotificationConfigs/{configId}", produces = A2A_MEDIA_TYPE)
    public TaskPushNotificationConfig getPushConfig(
            @PathVariable String taskId,
            @PathVariable String configId,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version) throws A2AError {
        authorizationService.requireA2aTaskScope(taskId);
        return requestHandler.onGetTaskPushNotificationConfig(
                new GetTaskPushNotificationConfigParams(taskId, configId), callContext(version, false));
    }

    @Operation(summary = "删除任务推送回调配置")
    @DeleteMapping(value = "/a2a/tasks/{taskId}/pushNotificationConfigs/{configId}")
    public void deletePushConfig(
            @PathVariable String taskId,
            @PathVariable String configId,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String version) throws A2AError {
        authorizationService.requireA2aTaskScope(taskId);
        requestHandler.onDeleteTaskPushNotificationConfig(
                new DeleteTaskPushNotificationConfigParams(taskId, configId), callContext(version, false));
    }

    /**
     * 组装ServerCallContext调用上下文
     * <p>封装传输层标识、协议版本，透传给RequestHandler业务处理层。</p>
     *
     * @param version 从Header取出的A2A‑Version版本字符串
     * @return ServerCallContext 调用上下文对象
     */
    private ServerCallContext callContext(String version, boolean streaming) {
        return new ServerCallContext(null,
                Map.of(ServerCallContext.TRANSPORT_KEY, TransportProtocol.HTTP_JSON.asString(),
                        A2aMetadataConstant.STREAMING_REQUEST_STATE, streaming), Set.of(), version);
    }
}
