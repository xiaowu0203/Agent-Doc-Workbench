package com.agentdoc.agent.execution.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.nio.charset.StandardCharsets;

/**
 * 工具返回结果大小限制装饰回调（装饰器模式）
 * <p>
 * 包装原始 {@link ToolCallback}，对工具执行返回的结果字符串做UTF‑8字节大小校验。
 * 用于防护MCP/Skill工具返回超大文本结果，避免造成Agent上下文暴涨、内存占用过高、大模型请求 payload超限等问题。
 * </p>
 * <p>
 * 行为说明：
 * <ul>
 * <li>工具定义、工具元数据全部直接透传给底层delegate，不做任何修改；</li>
 * <li>两个call重载均先委托底层工具执行拿到返回字符串，再进入{@link #requireSize(String)}做字节校验；</li>
 * <li>结果UTF‑8字节数超过maxBytes阈值时抛出{@link IllegalStateException}终止任务；</li>
 * <li>null结果直接放行，不做大小校验。</li>
 * </ul>
 * </p>
 * <p>
 * 注意：校验发生在工具执行完成之后；工具逻辑依然完整执行，只是返回结果超限才抛出异常，无法中断工具内部已经运行的业务。
 * </p>
 */
public final class ToolResultSizeLimitCallback implements ToolCallback {
    /**
     * 被包装的原始工具回调，真实执行工具调用逻辑
     */
    private final ToolCallback delegate;
    /**
     * 允许工具返回结果最大UTF‑8字节数；超出该阈值抛出异常
     */
    private final int maxBytes;

    public ToolResultSizeLimitCallback(ToolCallback delegate, int maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    /**
     * 获取工具定义，直接透传原始工具定义
     * @return 原始工具ToolDefinition
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    /**
     * 获取工具元数据，直接透传原始工具元数据
     * @return 原始工具ToolMetadata
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * 无上下文工具调用：执行底层工具，对返回结果做大小校验
     * @param input 工具调用入参字符串
     * @return 工具返回结果；超限抛出异常
     * @throws IllegalStateException 工具返回结果UTF‑8字节超出maxBytes限制抛出
     */
    @Override
    public String call(String input) {
        return requireSize(delegate.call(input));
    }

    /**
     * 携带ToolContext上下文工具调用：执行底层工具，对返回结果做大小校验
     * @param input 工具调用入参字符串
     * @param context 工具运行上下文
     * @return 工具返回结果；超限抛出异常
     * @throws IllegalStateException 工具返回结果UTF‑8字节超出maxBytes限制抛出
     */
    @Override
    public String call(String input, ToolContext context) {
        return requireSize(delegate.call(input, context));
    }

    /**
     * 校验工具返回结果UTF‑8字节大小
     * @param result 工具原始返回字符串，可以为null
     * @return 原始结果字符串
     * @throws IllegalStateException 非null结果字节超过maxBytes抛出
     */
    private String requireSize(String result) {
        if (result != null && result.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalStateException("MCP 工具结果超过大小限制");
        }
        return result;
    }
}
