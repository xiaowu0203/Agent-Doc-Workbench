package com.agentdoc.agent.execution;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.enums.ModelProvider;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于Spring‑AI实现的Agent运行时 {@link AgentExecutionRuntime}
 * <p>
 * 职责：建立MCP客户端连接、构建OpenAI兼容ChatClient、执行LLM+MCP工具调用完整会话；
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

    public SpringAiAgentExecutionRuntime(AgentConfigCryptoService cryptoService, PromptService promptService) {
        this.cryptoService = cryptoService;
        this.promptService = promptService;
    }

    /**
     * Agent执行入口：建立MCP连接，初始化ChatClient，发起LLM会话并自动调用MCP工具
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
        // 执行前先校验是否已经被取消
        requireNotCanceled(cancelRequested);
        // 校验模型提供商（仅做枚举解析，无实际调用）
        ModelProvider.fromCode(model.getProvider());
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

            // 构造ChatClient，发起对话：systemPrompt + 用户指令，自动执行MCP工具
            ChatResponse response = chatClient(agent, model, input.tokenBudget(), mcpClient, cancelRequested)
                    .prompt()
                    .system(promptService.systemPrompt(agent.getSystemPrompt()))
                    .user(instruction)
                    .call()
                    .chatResponse();
            requireNotCanceled(cancelRequested);
            // 解析模型返回结果与token用量
            return result(response);
        }
    }

    /**
     * 构建Spring‑AI ChatClient：解密ApiKey、设置模型参数、绑定MCP工具回调、注入迭代/取消校验逻辑
     * @param agent Agent配置
     * @param model 模型配置
     * @param taskTokenBudget 任务级Token预算，优先级高于agent配置
     * @param mcpClient MCP同步客户端，提供工具列表与工具调用能力
     * @param cancelRequested 取消信号回调
     * @return 可执行对话的ChatClient
     */
    private ChatClient chatClient(AgentEntity agent, ModelEntity model, Long taskTokenBudget,
                                  McpSyncClient mcpClient, BooleanSupplier cancelRequested) {
        // 构建OpenAI兼容API客户端，解密数据库密文API‑Key
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl(model))
                .apiKey(cryptoService.decrypt(model.getEncryptedApiKey()))
                .build();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(model.getModelKey());

        // 计算maxCompletionTokens，取任务预算、Agent预算、模型上限三者最小值
        Integer maxOutputTokens = maxOutputTokens(taskTokenBudget, agent, model);
        if (maxOutputTokens != null) {
            optionsBuilder.maxCompletionTokens(maxOutputTokens);
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(optionsBuilder.build())
                // 注入自定义工具执行判断逻辑：做最大迭代次数控制 + 取消信号检测
                .toolExecutionEligibilityPredicate(iterationLimit(agent.getMaxIterations(), cancelRequested))
                .build();

        // 绑定MCP工具回调Provider，Spring‑AI遇到tool_call时自动调用MCP服务端工具
        return ChatClient.builder(chatModel)
                .defaultToolCallbacks(SyncMcpToolCallbackProvider.builder()
                        .addMcpClient(mcpClient)
                        .build())
                .build();
    }

    /**
     * 自定义工具执行判定谓词：叠加最大迭代次数限制与取消信号检测
     * <p>Spring‑AI每一轮工具调用前会执行该Predicate；
     * 计数迭代轮次，超过maxIterations抛出异常；每次都检测取消信号。</p>
     * @param maxIterations Agent最大工具迭代轮次
     * @param cancelRequested 取消信号回调
     * @return ToolExecutionEligibilityPredicate 谓词
     */
    private ToolExecutionEligibilityPredicate iterationLimit(Integer maxIterations,
                                                              BooleanSupplier cancelRequested) {
        DefaultToolExecutionEligibilityPredicate delegate = new DefaultToolExecutionEligibilityPredicate();
        AtomicInteger iterations = new AtomicInteger();
        int limit = maxIterations == null ? AgentConstant.DEFAULT_MAX_ITERATIONS : maxIterations;
        return (options, response) -> {
            // 每一轮工具循环先检测是否取消
            requireNotCanceled(cancelRequested);
            boolean executionRequired = delegate.test(options, response);
            // 需要继续调用工具时计数+1，超过上限抛出异常终止Agent
            if (executionRequired && iterations.incrementAndGet() > limit) {
                throw new IllegalStateException("Agent 工具调用超过最大迭代次数");
            }
            return executionRequired;
        };
    }

    /**
     * 解析ChatResponse，组装AgentRuntimeResult结果对象，提取文本摘要与token消耗
     * @param response Spring‑AI ChatResponse
     * @return AgentRuntimeResult
     */
    private AgentRuntimeResult result(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回执行结果");
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        long inputTokens = usage == null || usage.getPromptTokens() == null ? 0L : usage.getPromptTokens();
        long outputTokens = usage == null || usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens();
        Long cachedInputTokens = cachedInputTokens(usage);
        return new AgentRuntimeResult(
                response.getResult().getOutput().getText(), inputTokens, cachedInputTokens, outputTokens);
    }

    /**
     * 从OpenAI原生usage中提取cached‑tokens（缓存命中输入token）
     * @param usage Spring‑AI封装的Usage对象
     * @return cachedTokens，无缓存信息返回null
     */
    private Long cachedInputTokens(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof OpenAiApi.Usage nativeUsage)
                || nativeUsage.promptTokensDetails() == null
                || nativeUsage.promptTokensDetails().cachedTokens() == null) {
            return null;
        }
        return nativeUsage.promptTokensDetails().cachedTokens().longValue();
    }

    /**
     * 计算输出Token最大上限：取【任务预算、Agent预算、模型本身上限】的最小值
     * @param taskTokenBudget 任务维度token预算（入参携带）
     * @param agent Agent配置
     * @param model 模型配置
     * @return 安全的maxCompletionTokens；null表示不做限制
     */
    private Integer maxOutputTokens(Long taskTokenBudget, AgentEntity agent, ModelEntity model) {
        Long budget = taskTokenBudget == null ? agent.getTokenBudget() : taskTokenBudget;
        Long modelLimit = model.getMaxOutputTokens();
        Long limit = budget == null ? modelLimit : modelLimit == null ? budget : Math.min(budget, modelLimit);
        return limit == null ? null : Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
    }

    /**
     * 获取模型baseUrl，为空时使用默认OpenAI兼容地址
     * @param model 模型配置实体
     * @return baseUrl地址字符串
     */
    private String baseUrl(ModelEntity model) {
        return model.getBaseUrl() == null || model.getBaseUrl().isBlank()
                ? AgentConstant.DEFAULT_OPENAI_BASE_URL
                : model.getBaseUrl();
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
