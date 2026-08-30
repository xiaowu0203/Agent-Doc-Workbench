package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelTurnResult;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.agent.execution.runtime.AgentExecutionLimitExceededException;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.agent.execution.audit.AgentExecutionModelCallAuditService;
import com.agentdoc.common.pojo.TokenValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 厂商无关Agent多轮工具调用循环内核
 * <p>
 * 自研完整Agent会话主循环，负责驱动 LLM调用、工具调度、会话流转、熔断与任务取消。
 * 不依赖第三方Agent黑盒实现，每一轮会话生命周期均可介入控制。
 * 支持同步/流式两种输出模式；提供Token预算、最大迭代次数、任务取消三类保护能力。
 * </p>
 * <p>
 * 循环流程简述：
 * <ol>
 * <li>校验任务取消标记 + Token预算校验</li>
 * <li>计算本轮输出Token上限，调用大模型（同步/流式）</li>
 * <li>补齐/估算Token用量，累加总消耗</li>
 * <li>无工具调用：直接返回最终结果结束会话</li>
 * <li>存在工具调用：校验最大迭代，执行工具，更新会话历史，进入下一轮循环</li>
 * </ol>
 * </p>
 * @see CancellationAwareToolCallback
 * @see TokenUsageEstimator
 */
@Component
@Slf4j
public class ProviderNeutralToolLoop {

    /** Token用量补齐、估算组件，当模型未返回usage时做字节估算 */
    private final TokenUsageEstimator tokenUsageEstimator;
    private final AgentExecutionModelCallAuditService modelCallAuditService;

    public ProviderNeutralToolLoop(TokenUsageEstimator tokenUsageEstimator,
                                   AgentExecutionModelCallAuditService modelCallAuditService) {
        this.tokenUsageEstimator = tokenUsageEstimator;
        this.modelCallAuditService = modelCallAuditService;
    }

    /**
     * 执行Agent会话（同步非流式）
     *
     * @param adapter 模型调用适配器
     * @param context 模型执行上下文，携带工具列表、模型参数
     * @param systemPrompt 系统提示词
     * @param instruction 用户输入指令
     * @param tokenBudget 全局Token预算上限，null代表不做预算限制
     * @param maxIterations 最大工具迭代轮次，防止无限循环
     * @param cancelRequested 任务取消状态查询函数，返回true表示任务请求取消
     * @return Agent最终运行结果，包含文本输出与完整Token消耗统计
     * @throws AgentExecutionCanceledException 任务被取消时抛出
     * @throws AgentExecutionLimitExceededException 超过最大工具迭代轮次抛出
     * @throws IllegalStateException Token预算耗尽、无法获取token用量时抛出
     */
    public AgentRuntimeResult execute(ModelAdapter adapter, ModelAdapterContext context,
                                       String systemPrompt, String instruction,
                                       Long tokenBudget, int maxIterations,
                                       BooleanSupplier cancelRequested) {
        return executeInternal(adapter, context, systemPrompt, instruction, tokenBudget, maxIterations,
                cancelRequested, ignored -> { }, false);
    }

    /**
     * 执行Agent会话（流式输出）
     *
     * @param adapter 模型调用适配器
     * @param context 模型执行上下文，携带工具列表、模型参数
     * @param systemPrompt 系统提示词
     * @param instruction 用户输入指令
     * @param tokenBudget 全局Token预算上限，null代表不做预算限制
     * @param maxIterations 最大工具迭代轮次，防止无限循环
     * @param cancelRequested 任务取消状态查询函数，返回true表示任务请求取消
     * @param onTextDelta 文本增量回调，接收流式返回的片段文本
     * @return Agent最终运行结果，包含完整文本输出与Token消耗统计
     * @throws AgentExecutionCanceledException 任务被取消时抛出
     * @throws AgentExecutionLimitExceededException 超过最大工具迭代轮次抛出
     * @throws IllegalStateException Token预算耗尽、无法获取token用量时抛出
     */
    public AgentRuntimeResult execute(ModelAdapter adapter, ModelAdapterContext context,
                                       String systemPrompt, String instruction,
                                       Long tokenBudget, int maxIterations,
                                       BooleanSupplier cancelRequested,
                                       Consumer<String> onTextDelta) {
        return executeInternal(adapter, context, systemPrompt, instruction, tokenBudget, maxIterations,
                cancelRequested, onTextDelta, true);
    }

    /**
     * Agent会话内部主循环实现
     * @param adapter 模型适配器
     * @param context 模型上下文
     * @param systemPrompt 系统prompt
     * @param instruction 用户指令
     * @param tokenBudget token总预算
     * @param maxIterations 最大工具迭代轮次
     * @param cancelRequested 取消状态源
     * @param onTextDelta 流式delta回调，非流式下为空实现
     * @param streaming true=流式，false=同步调用
     * @return Agent执行结果
     */
    private AgentRuntimeResult executeInternal(ModelAdapter adapter, ModelAdapterContext context,
                                                String systemPrompt, String instruction,
                                                Long tokenBudget, int maxIterations,
                                                BooleanSupplier cancelRequested,
                                                Consumer<String> onTextDelta,
                                                boolean streaming) {
        // 构建工具调用管理器，注入已经包装好取消校验的ToolCallback列表
        ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(context.toolCallbacks()))
                .build();

        // 构建Prompt
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(instruction)));
        // Token用量统计记录对象
        TokenUsage totalUsage = null;
        int toolIterations = 0;
        int modelSequence = 0;

        while (true) {
            // 每轮循环入口检查任务取消
            requireNotCanceled(cancelRequested);
            // 校验全局token预算是否超限
            ensureBudget(tokenBudget, totalUsage);

            // 计算本轮允许最大输出token：取模型上限与剩余预算的较小值
            ModelAdapterContext turnContext = context.withMaxOutputTokens(
                    remainingOutputLimit(context.maxOutputTokens(), tokenBudget, totalUsage));

            // 创建并启动一条模型调用审计记录
            AgentExecutionModelCallEntity modelCall = modelCallAuditService.start(
                    turnContext, ++modelSequence, prompt.getInstructions(), streaming);
            ModelTurnResult turn;
            try {
                // 根据streaming标志选择同步或者流式调用LLM
                turn = streaming
                        ? adapter.stream(turnContext, prompt.getInstructions(), onTextDelta)
                        : adapter.callOnce(turnContext, prompt.getInstructions());
            } catch (RuntimeException exception) {
                // 标记模型调用审计为失败结束
                finishFailed(modelCall, exception);
                throw exception;
            }
            try {
                // 标记模型调用审计为成功结束
                modelCallAuditService.succeed(modelCall, turn.response());
            } catch (RuntimeException exception) {
                log.error("模型调用已成功但结束审计失败: executionId={}, auditId={}, sequence={}",
                        turnContext.executionId(), modelCall.getId(), modelSequence, exception);
            }

            // 补齐token用量，接口返回不足时执行估算
            TokenUsage turnUsage = tokenUsageEstimator.complete(turn.tokenUsage(), prompt.getInstructions(),
                    context.toolCallbacks(), turn.response());
            totalUsage = totalUsage == null ? turnUsage : totalUsage.add(turnUsage);

            // LLM返回后再次校验取消与预算
            requireNotCanceled(cancelRequested);
            ensureBudget(tokenBudget, totalUsage);

            ChatResponse response = turn.response();
            // 无工具调用，会话正常结束，返回结果
            if (!response.hasToolCalls()) {
                return new AgentRuntimeResult(turn.text(), totalUsage);
            }
            // 判断是否达到最大工具迭代轮次，防止死循环
            if (toolIterations++ >= maxIterations) {
                throw new AgentExecutionLimitExceededException(maxIterations);
            }

            requireNotCanceled(cancelRequested);
            // 执行工具调用，内部ToolCallback已经被CancellationAwareToolCallback装饰
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
            requireNotCanceled(cancelRequested);

            // 更新会话历史，进入下一轮while循环
            prompt = new Prompt(toolExecutionResult.conversationHistory());
        }
    }

    private void finishFailed(AgentExecutionModelCallEntity modelCall, RuntimeException exception) {
        try {
            modelCallAuditService.fail(modelCall, exception.getClass().getSimpleName());
        } catch (RuntimeException auditException) {
            log.error("模型调用失败且结束审计失败: executionId={}, auditId={}, sequence={}",
                    modelCall.getExecutionId(), modelCall.getId(), modelCall.getSequenceNo(), auditException);
        }
    }

    /**
     * 计算本轮模型可使用的输出token上限
     * @param modelLimit 模型本身最大输出token限制
     * @param tokenBudget Agent全局token总预算，null表示无限制
     * @param totalUsage 累计已经消耗的token用量
     * @return 本轮输出token上限
     * @throws IllegalStateException 剩余预算<=0时抛出预算耗尽异常
     */
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

    /**
     * 校验累计 Token 用量是否超过执行预算。
     *
     * @param tokenBudget Token 总预算；为空表示不限制
     * @param totalUsage  当前累计用量
     * @throws IllegalStateException 累计用量超过预算时抛出
     */
    private void ensureBudget(Long tokenBudget, TokenUsage totalUsage) {
        if (tokenBudget != null && totalUsage != null && usedTokens(totalUsage) > tokenBudget) {
            throw new IllegalStateException("Agent Token 预算已超限");
        }
    }

    /**
     * 统计输入+输出总消耗token
     * @param usage token用量对象
     * @return input + output合计token数量
     */
    private long usedTokens(TokenUsage usage) {
        return requiredValue(usage.input(), "input") + requiredValue(usage.output(), "output");
    }

    /**
     * 获取TokenValue的实际数值，不可用时抛出异常阻断流程
     * @param value token值对象
     * @param name 标记是input还是output，用于错误提示
     * @return 实际token数值
     * @throws IllegalStateException 无法获取token数值抛出
     */
    private long requiredValue(TokenValue value, String name) {
        if (!value.available()) {
            throw new IllegalStateException("无法获取或估算 " + name + " Token 用量");
        }
        return value.value();
    }

    /**
     * 检查任务取消标记，已请求取消则抛出异常中断Agent执行
     * @param cancelRequested 取消状态查询源
     * @throws AgentExecutionCanceledException 任务已取消抛出
     */
    private void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) {
            throw new AgentExecutionCanceledException();
        }
    }
}
