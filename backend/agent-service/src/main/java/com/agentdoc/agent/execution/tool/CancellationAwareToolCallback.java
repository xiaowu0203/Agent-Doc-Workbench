package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.BooleanSupplier;

/**
 * 工具调用装饰器：为工具执行增加任务取消校验能力
 * <p>
 * 在原始工具执行【之前】和【执行完成之后】分别检查任务取消标记。
 * 防止任务已经被取消的情况下，继续执行工具逻辑或继续往下流转会话。
 * 一旦检测到取消标记，抛出 {@link AgentExecutionCanceledException} 中断执行。
 * </p>
 */
public class CancellationAwareToolCallback implements ToolCallback {

    /** 被包装的原始工具回调实现 */
    private final ToolCallback delegate;
    /** 任务取消状态判断源：返回true代表任务已请求取消 */
    private final BooleanSupplier cancelRequested;

    public CancellationAwareToolCallback(ToolCallback delegate, BooleanSupplier cancelRequested) {
        this.delegate = delegate;
        this.cancelRequested = cancelRequested;
    }


    @Override
    public ToolDefinition getToolDefinition() {
        // 工具元数据直接透传给原始实现，不做增强
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        // 工具元数据直接透传给原始实现，不做增强
        return delegate.getToolMetadata();
    }

    /**
     * 执行工具调用（无上下文版本）
     * <p>执行前校验取消 → 调用原始工具 → 返回结果前再次校验取消</p>
     * @param input 工具入参JSON字符串
     * @return 工具执行返回结果
     * @throws AgentExecutionCanceledException 任务已被取消时抛出
     */
    @Override
    public String call(String input) {
        // 校验任务是否被取消
        requireNotCanceled();
        // 调用工具并拿到结果
        String result = delegate.call(input);
        // 校验任务是否被取消
        requireNotCanceled();
        return result;
    }

    /**
     * 执行工具调用（携带ToolContext上下文版本）
     * <p>执行前校验取消 → 调用原始工具 → 返回结果前再次校验取消</p>
     * @param input 工具入参JSON字符串
     * @param toolContext 工具执行上下文对象
     * @return 工具执行返回结果
     * @throws AgentExecutionCanceledException 任务已被取消时抛出
     */
    @Override
    public String call(String input, ToolContext toolContext) {
        // 校验任务是否被取消
        requireNotCanceled();
        // 调用工具并拿到结果
        String result = delegate.call(input, toolContext);
        // 校验任务是否被取消
        requireNotCanceled();
        return result;
    }

    /**
     * 检查任务取消标记，如果已请求取消，则抛出异常终止流程
     * @throws AgentExecutionCanceledException 任务取消标记为true时抛出
     */
    private void requireNotCanceled() {
        if (cancelRequested.getAsBoolean()) {
            throw new AgentExecutionCanceledException();
        }
    }
}
