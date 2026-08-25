package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.model.ModelCapabilities;
import com.agentdoc.agent.execution.tool.CancellationAwareToolCallback;
import com.agentdoc.agent.execution.tool.ProviderNeutralToolLoop;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.service.PromptService;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 基于Spring‑AI实现的Agent运行时 {@link AgentExecutionRuntime}
 * <p>
 * 职责：建立MCP客户端连接、选择模型适配器、执行LLM+MCP工具调用完整会话；
 * 处理取消信号、最大工具迭代次数、Token预算上限、API密钥解密、MCP HTTP传输；
 * 不操作数据库，纯内存运行时，输出 {@link AgentRuntimeResult} 返回上层业务服务。
 * </p>
 * <p>取消机制：多处调用 requireNotCanceled() 轮询外部传入的cancelRequested回调，
 * 检测到取消标记立刻抛出 {@link AgentExecutionCanceledException} 终止执行。</p>
 */
@Component
public class SpringAiAgentExecutionRuntime implements AgentExecutionRuntime {
    /** 配置加密解密服务，解密模型存储的encryptedApiKey */
    private final AgentConfigCryptoService cryptoService;
    /** Prompt服务，获取系统提示词 */
    private final PromptService promptService;
    /** 模型适配器注册表，隔离不同厂商/协议的 SDK 差异 */
    private final ModelAdapterRegistry adapterRegistry;
    /** 厂商无关工具循环，统一控制工具执行和多轮模型调用 */
    private final ProviderNeutralToolLoop toolLoop;

    public SpringAiAgentExecutionRuntime(AgentConfigCryptoService cryptoService, PromptService promptService,
                                         ModelAdapterRegistry adapterRegistry,
                                         ProviderNeutralToolLoop toolLoop) {
        this.cryptoService = cryptoService;
        this.promptService = promptService;
        this.adapterRegistry = adapterRegistry;
        this.toolLoop = toolLoop;
    }

    /**
     * Agent执行入口：建立MCP连接，选择模型适配器，交由统一工具循环执行LLM会话
     * @param agent Agent配置实体
     * @param model LLM模型配置实体
     * @param instruction 用户输入指令
     * @param input 任务入参DTO，携带mcpServerUrl、taskCapability鉴权令牌等
     * @param cancelRequested 外部取消信号回调，轮询判断是否需要终止
     * @return 运行时结果，包含摘要文本、各类token消耗
     */
    @Override
    public AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                                      AgentTaskInputDTO input, BooleanSupplier cancelRequested) {
        return executeInternal(agent, model, instruction, input, cancelRequested, ignored -> { }, false);
    }

    @Override
    public AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                                      AgentTaskInputDTO input, BooleanSupplier cancelRequested,
                                      Consumer<String> onTextDelta) {
        return executeInternal(agent, model, instruction, input, cancelRequested, onTextDelta, true);
    }

    private AgentRuntimeResult executeInternal(AgentEntity agent, ModelEntity model, String instruction,
                                                AgentTaskInputDTO input, BooleanSupplier cancelRequested,
                                                Consumer<String> onTextDelta, boolean streaming) {
        // 执行前先校验是否已经被取消
        requireNotCanceled(cancelRequested);
        // MCP任务依赖模型发起工具调用，能力不满足时在建立MCP连接前直接失败
        ModelAdapter adapter = adapterRegistry.require(model, ModelCapabilities.requiredToolCalling());
        // 解析MCP服务端地址，拆分为baseUrl与path
        McpEndpoint endpoint = McpEndpoint.from(input.mcpServerUrl());

        // 构建WebClient，携带taskCapability令牌访问MCP服务
        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(endpoint.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        JwtConstant.TOKEN_TYPE_BEARER + " " + input.taskCapability());

        // 构建MCP HTTP流式传输层
        WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClientBuilder)
                .endpoint(endpoint.path())
                .build();

        // try‑with‑resources：执行结束自动关闭MCP客户端连接
        try (McpSyncClient mcpClient = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation(
                        AgentConstant.MCP_CLIENT_NAME, AgentConstant.MCP_CLIENT_VERSION))
                .requestTimeout(Duration.ofSeconds(agent.getExecutionTimeoutSeconds()))
                .build()) {
            // MCP握手初始化
            mcpClient.initialize();
            requireNotCanceled(cancelRequested);

            List<ToolCallback> toolCallbacks = Arrays.stream(new SyncMcpToolCallbackProvider(mcpClient)
                            .getToolCallbacks())
                    .map(callback -> new CancellationAwareToolCallback(callback, cancelRequested))
                    .map(ToolCallback.class::cast)
                    .toList();
            Integer maxOutputTokens = modelMaxOutputTokens(model);
            ModelAdapterContext adapterContext = new ModelAdapterContext(agent, model,
                    cryptoService.decrypt(model.getEncryptedApiKey()), maxOutputTokens, toolCallbacks);
            Long tokenBudget = input.tokenBudget() == null ? agent.getTokenBudget() : input.tokenBudget();
            int maxIterations = agent.getMaxIterations() == null
                    ? AgentConstant.DEFAULT_MAX_ITERATIONS : agent.getMaxIterations();
            if (streaming) {
                return toolLoop.execute(adapter, adapterContext,
                        promptService.systemPrompt(agent.getSystemPrompt()), instruction,
                        tokenBudget, maxIterations, cancelRequested, onTextDelta);
            }
            return toolLoop.execute(adapter, adapterContext,
                    promptService.systemPrompt(agent.getSystemPrompt()), instruction,
                    tokenBudget, maxIterations, cancelRequested);
        }
    }
    /**
     * 读取模型自身的单轮输出Token上限。
     * <p>任务和Agent预算由 {@link ProviderNeutralToolLoop} 按累计用量统一熔断。</p>
     * @param model 模型配置
     * @return 安全的单轮maxCompletionTokens；null表示不做模型级限制
     */
    private Integer modelMaxOutputTokens(ModelEntity model) {
        Long limit = model.getMaxOutputTokens();
        return limit == null ? null : Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
    }

    /**
     * 检测取消信号，收到取消直接抛出异常终止Agent流程
     * @param cancelRequested 外部回调布尔供应器
     */
    private void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) {
            throw new AgentExecutionCanceledException();
        }
    }

    /**
     * MCP服务端点记录类，把传入的完整serverUrl拆分为 baseUrl(协议+主机端口) 和 path(请求路径)
     * @param baseUrl 协议+主机，例如 http://127.0.0.1:8080
     * @param path http请求路径，例如 /mcp
     */
    private record McpEndpoint(String baseUrl, String path) {

        /**
         * 从完整MCP Server URL解析出McpEndpoint
         * @param serverUrl 完整MCP服务地址
         * @return McpEndpoint
         */
        private static McpEndpoint from(String serverUrl) {
            URI uri = URI.create(serverUrl);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                throw new IllegalArgumentException("MCP Server URL 必须是绝对地址");
            }
            String path = uri.getRawPath();
            return new McpEndpoint(
                    uri.getScheme() + "://" + uri.getAuthority(),
                    path == null || path.isBlank() ? AgentConstant.DEFAULT_MCP_ENDPOINT : path);
        }
    }
}
