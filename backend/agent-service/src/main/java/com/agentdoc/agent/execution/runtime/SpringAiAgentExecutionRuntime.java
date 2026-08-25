package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.model.ModelCapabilities;
import com.agentdoc.agent.execution.tool.ProviderNeutralToolLoop;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.service.PromptService;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import org.springframework.stereotype.Component;

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
@Component("customAgentExecutionRuntime")
@ConditionalOnAgentRuntime(AgentRuntimeType.CUSTOM)
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
     * 同步执行入口，不开启流式文本delta回调
     */
    @Override
    public AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                                      AgentTaskInputDTO input, BooleanSupplier cancelRequested) {
        return executeInternal(agent, model, instruction, input, cancelRequested, ignored -> { }, false);
    }

    /**
     * 流式执行入口，支持onTextDelta增量文本回调
     * @param onTextDelta 每收到一段模型输出文本片段就回调该消费者
     */
    @Override
    public AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                                      AgentTaskInputDTO input, BooleanSupplier cancelRequested,
                                      Consumer<String> onTextDelta) {
        return executeInternal(agent, model, instruction, input, cancelRequested, onTextDelta, true);
    }

    /**
     * 内部统一执行入口：组装桥接控制器、用量收集器、包装ObservingChatModel，构建ReactAgent并执行
     * @param agent agent配置实体
     * @param model 模型配置实体
     * @param instruction 用户顶层指令
     * @param input 任务入参
     * @param cancelRequested 外部取消信号
     * @param onTextDelta 流式文本增量回调
     * @param streaming 是否启用流式模式
     * @return 标准化Agent执行结果
     */
    private AgentRuntimeResult executeInternal(AgentEntity agent, ModelEntity model, String instruction,
                                                AgentTaskInputDTO input, BooleanSupplier cancelRequested,
                                                Consumer<String> onTextDelta, boolean streaming) {
        // 执行前先校验是否已经被取消
        requireNotCanceled(cancelRequested);
        // MCP任务依赖模型发起工具调用，能力不满足时在建立MCP连接前直接失败
        ModelAdapter adapter = adapterRegistry.require(model, ModelCapabilities.requiredToolCalling());
        // 任务级MCP资源封装负责鉴权、初始化、取消检测和连接释放
        try (TaskScopedMcpTools tools = TaskScopedMcpTools.open(input.mcpServerUrl(),
                input.taskCapability(), timeoutSeconds(agent), cancelRequested)) {
            Integer maxOutputTokens = modelMaxOutputTokens(model);
            ModelAdapterContext adapterContext = new ModelAdapterContext(agent, model,
                    cryptoService.decrypt(model.getEncryptedApiKey()), maxOutputTokens, tools.callbacks());
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
     * 获取agent配置的执行超时秒数；null使用全局默认常量
     */
    private int timeoutSeconds(AgentEntity agent) {
        return agent.getExecutionTimeoutSeconds() == null
                ? AgentConstant.DEFAULT_EXECUTION_TIMEOUT_SECONDS
                : agent.getExecutionTimeoutSeconds();
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

}
