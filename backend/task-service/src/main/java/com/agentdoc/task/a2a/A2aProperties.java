package com.agentdoc.task.a2a;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A2A 客户端和任务对账配置。
 */
@Data
@ConfigurationProperties(prefix = "agent-doc.a2a")
public class A2aProperties {

    /** Agent Server 服务根地址。 */
    private String agentServiceUrl = "http://localhost:8084";

    /** Agent Server 推送任务状态的回调地址。 */
    private String taskCallbackUrl = "http://localhost:8083/api/task/internal/a2a/events";

    /** Agent Server 调用 Workbench MCP Server 的地址。 */
    private String mcpServerUrl = "http://localhost:8083/mcp";

    /** A2A 协议请求路径。 */
    private Paths paths = new Paths();

    /** A2A 状态对账调度间隔，单位毫秒。 */
    private long reconcileDelayMs = 60_000L;

    /** A2A 任务心跳过期阈值，单位秒。 */
    private long reconcileStaleSeconds = 120L;

    /** 单次 A2A 对账任务批次大小。 */
    private int reconcileBatchSize = 50;

    @Data
    public static class Paths {

        /** 提交消息/创建任务路径。 */
        private String send = "/a2a/message:send";

        /** 查询任务路径，必须包含 {@code {taskId}} 占位符。 */
        private String task = "/a2a/tasks/{taskId}";

        /** 取消任务路径，必须包含 {@code {taskId}} 占位符。 */
        private String cancel = "/a2a/tasks/{taskId}:cancel";
    }
}
