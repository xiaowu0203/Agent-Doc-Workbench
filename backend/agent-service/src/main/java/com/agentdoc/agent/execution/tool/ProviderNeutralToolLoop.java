package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelTurnResult;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 厂商无关的模型工具循环。
 * <p>适配器每次只调用一次模型，本类统一决定是否执行工具以及是否继续下一轮。</p>
 */
@Component
public class ProviderNeutralToolLoop {

    private final TokenUsageEstimator tokenUsageEstimator;

    public ProviderNeutralToolLoop(TokenUsageEstimator tokenUsageEstimator) {
        this.tokenUsageEstimator = tokenUsageEstimator;
    }

    public AgentRuntimeResult execute(ModelAdapter adapter, ModelAdapterContext context,
                                       String systemPrompt, String instruction,
                                       Long tokenBudget, int maxIterations,
                                       BooleanSupplier cancelRequested) {
        return executeInternal(adapter, context, systemPrompt, instruction, tokenBudget, maxIterations,
                cancelRequested, ignored -> { }, false);
    }

    public AgentRuntimeResult execute(ModelAdapter adapter, ModelAdapterContext context,
                                       String systemPrompt, String instruction,
                                       Long tokenBudget, int maxIterations,
                                       BooleanSupplier cancelRequested,
                                       Consumer<String> onTextDelta) {
        return executeInternal(adapter, context, systemPrompt, instruction, tokenBudget, maxIterations,
                cancelRequested, onTextDelta, true);
    }

    private AgentRuntimeResult executeInternal(ModelAdapter adapter, ModelAdapterContext context,
                                                String systemPrompt, String instruction,
                                                Long tokenBudget, int maxIterations,
                                                BooleanSupplier cancelRequested,
                                                Consumer<String> onTextDelta,
                                                boolean streaming) {
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(context.toolCallbacks()))
                .build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(instruction)));
        TokenUsage totalUsage = null;
        int toolIterations = 0;

        while (true) {
            requireNotCanceled(cancelRequested);
            ensureBudget(tokenBudget, totalUsage);
            ModelAdapterContext turnContext = context.withMaxOutputTokens(
                    remainingOutputLimit(context.maxOutputTokens(), tokenBudget, totalUsage));

            ModelTurnResult turn = streaming
                    ? adapter.stream(turnContext, prompt.getInstructions(), onTextDelta)
                    : adapter.callOnce(turnContext, prompt.getInstructions());
            TokenUsage turnUsage = tokenUsageEstimator.complete(turn.tokenUsage(), prompt.getInstructions(),
                    context.toolCallbacks(), turn.response());
            totalUsage = totalUsage == null ? turnUsage : totalUsage.add(turnUsage);
            requireNotCanceled(cancelRequested);
            ensureBudget(tokenBudget, totalUsage);

            ChatResponse response = turn.response();
            if (!response.hasToolCalls()) {
                return new AgentRuntimeResult(turn.text(), totalUsage);
            }
            if (toolIterations++ >= maxIterations) {
                throw new IllegalStateException("Agent 工具调用超过最大迭代次数");
            }

            requireNotCanceled(cancelRequested);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
            requireNotCanceled(cancelRequested);
            prompt = new Prompt(toolExecutionResult.conversationHistory());
        }
    }

    private Integer remainingOutputLimit(Integer modelLimit, Long tokenBudget, TokenUsage totalUsage) {
        if (tokenBudget == null) {
            return modelLimit;
        }
        long used = totalUsage == null ? 0L : usedTokens(totalUsage);
        long remaining = tokenBudget - used;
        if (remaining <= 0) {
            throw new IllegalStateException("Agent Token 预算已耗尽");
        }
        long limit = modelLimit == null ? remaining : Math.min(modelLimit, remaining);
        return Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
    }

    private void ensureBudget(Long tokenBudget, TokenUsage totalUsage) {
        if (tokenBudget != null && totalUsage != null && usedTokens(totalUsage) > tokenBudget) {
            throw new IllegalStateException("Agent Token 预算已超限");
        }
    }

    private long usedTokens(TokenUsage usage) {
        return requiredValue(usage.input(), "input") + requiredValue(usage.output(), "output");
    }

    private long requiredValue(TokenValue value, String name) {
        if (!value.available()) {
            throw new IllegalStateException("无法获取或估算 " + name + " Token 用量");
        }
        return value.value();
    }

    private void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) {
            throw new AgentExecutionCanceledException();
        }
    }
}
