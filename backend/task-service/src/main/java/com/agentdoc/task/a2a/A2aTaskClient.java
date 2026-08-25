package com.agentdoc.task.a2a;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.a2aproject.sdk.spec.AuthenticationInfo;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * A2A Agent‑to‑Agent 远端任务HTTP客户端
 * <p>
 * 封装对远端Agent‑Server A2A协议接口调用，提供任务提交、查询、取消能力。
 * 统一携带A2A协议版本头、Authorization Bearer令牌、A2A专用Content‑Type。
 * 提交任务时配置回调通知地址，用于Agent‑Server主动推送任务状态变更回调。
 * </p>
 */
@Component
public class A2aTaskClient {

    /**
     * A2A回调通知HTTP请求头：携带通知鉴权令牌
     */
    public static final String NOTIFICATION_TOKEN_HEADER = "X-A2A-Notification-Token";
    /**
     * A2A协议专用媒体类型 application/a2a+json
     */
    private static final String A2A_MEDIA_TYPE = "application/a2a+json";
    /**
     * A2A协议版本请求头名称
     */
    private static final String A2A_VERSION_HEADER = "A2A-Version";
    /**
     * 当前使用A2A协议版本号
     */
    private static final String A2A_VERSION = "1.0";
    /**
     * 提交A2A消息/任务接口路径 POST
     */
    private static final String SEND_PATH = "/a2a/message:send";
    /**
     * 查询单个A2A任务接口路径 GET，占位符：taskId
     */
    private static final String TASK_PATH = "/a2a/tasks/{taskId}";
    /**
     * 取消A2A任务接口路径 POST，占位符：taskId
     */
    private static final String CANCEL_PATH = "/a2a/tasks/{taskId}:cancel";
    /**
     * Bearer token鉴权前缀
     */
    private static final String BEARER_PREFIX = JwtConstant.TOKEN_TYPE_BEARER + " ";

    /**
     * Spring RestClient HTTP客户端实例，指向远端Agent‑Service服务地址
     */
    private final RestClient restClient;
    /**
     * A2A任务回调地址，Agent‑Server状态变更时会POST到此地址
     */
    private final String callbackUrl;
    /**
     * MCP服务访问地址，透传给远端Agent，用于Agent调用本地MCP工具
     */
    private final String mcpServerUrl;

    /**
     * 构造A2A HTTP客户端
     *
     * @param builder            RestClient构建器
     * @param agentServiceUrl    远端Agent‑Server服务根地址，配置项 agent‑doc.a2a.agent‑service‑url
     * @param callbackUrl        A2A任务回调通知地址，配置项 agent‑doc.a2a.task‑callback‑url
     * @param mcpServerUrl       本地MCP服务地址，配置项 agent‑doc.a2a.mcp‑server‑url
     */
    public A2aTaskClient(RestClient.Builder builder,
                         @Value("${agent-doc.a2a.agent-service-url}") String agentServiceUrl,
                         @Value("${agent-doc.a2a.task-callback-url}") String callbackUrl,
                         @Value("${agent-doc.a2a.mcp-server-url}") String mcpServerUrl) {
        this.restClient = builder.baseUrl(agentServiceUrl).build();
        this.callbackUrl = callbackUrl;
        this.mcpServerUrl = mcpServerUrl;
    }

    /**
     * 向远端Agent‑Server提交任务，创建A2A异步任务
     * <p>组装任务输入、消息体、回调推送配置；设置立即返回模式，由远端通过callbackUrl推送状态回调。
     * 内部携带Bearer能力令牌、A2A版本头、A2A协议MediaType。</p>
     *
     * @param task       本地工作台任务实体
     * @param capability 任务能力令牌，用于鉴权与回调认证
     * @return 远端A2A任务对象
     */
    public Task send(TaskEntity task, String capability) {
        // 组装传给Agent的任务输入DTO，包含任务元信息、MCP服务地址
        AgentTaskInputDTO input = new AgentTaskInputDTO(
                task.getId(), task.getAgentId(), task.getSpaceId(), task.getDocumentId(), task.getTokenBudget(),
                mcpServerUrl, capability);
        // 构建A2A消息：用户指令文本 + 结构化任务输入数据Part
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(new TextPart(task.getInstruction()), new DataPart(input))
                .build();
        // 配置远端回调推送：指定回调地址、回调鉴权令牌
        TaskPushNotificationConfig pushConfig = TaskPushNotificationConfig.builder()
                .id(UUID.randomUUID().toString())
                .url(callbackUrl)
                .token(capability)
                .authentication(new AuthenticationInfo(JwtConstant.TOKEN_TYPE_BEARER, capability))
                .build();
        // 消息发送配置：returnImmediately=true，异步执行任务，不阻塞等待任务完成
        MessageSendConfiguration configuration = MessageSendConfiguration.builder()
                .returnImmediately(true)
                .taskPushNotificationConfig(pushConfig)
                .build();
        MessageSendParams params = MessageSendParams.builder()
                .message(message)
                .configuration(configuration)
                .build();
        // POST调用A2A send接口
        return restClient.post()
                .uri(SEND_PATH)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + capability)
                .header(A2A_VERSION_HEADER, A2A_VERSION)
                .contentType(MediaType.parseMediaType(A2A_MEDIA_TYPE))
                .accept(MediaType.parseMediaType(A2A_MEDIA_TYPE))
                .body(params)
                .retrieve()
                .body(Task.class);
    }

    /**
     * 根据远端taskId查询A2A任务最新状态信息
     *
     * @param taskId     远端A2A任务ID
     * @param capability 任务能力鉴权令牌
     * @return 远端A2A任务对象
     */
    public Task get(String taskId, String capability) {
        return restClient.get()
                .uri(TASK_PATH, taskId)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + capability)
                .header(A2A_VERSION_HEADER, A2A_VERSION)
                .accept(MediaType.parseMediaType(A2A_MEDIA_TYPE))
                .retrieve()
                .body(Task.class);
    }

    /**
     * 请求远端Agent‑Server取消正在执行的A2A任务
     *
     * @param taskId     远端A2A任务ID
     * @param capability 任务能力鉴权令牌
     * @return 取消后的远端A2A任务对象
     */
    public Task cancel(String taskId, String capability) {
        return restClient.post()
                .uri(CANCEL_PATH, taskId)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + capability)
                .header(A2A_VERSION_HEADER, A2A_VERSION)
                .accept(MediaType.parseMediaType(A2A_MEDIA_TYPE))
                .retrieve()
                .body(Task.class);
    }
}
