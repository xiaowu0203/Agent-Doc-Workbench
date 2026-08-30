package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.constant.McpConstant;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 命名空间包装工具回调（装饰器模式）
 * <p>
 * 用于解决不同MCP服务/来源之间工具名称冲突问题。
 * 将原始工具名称拼接 serverKey 前缀生成唯一工具名：{@code serverKey__原始工具名}，对外暴露给大模型；
 * 但底层真实工具调用依然委托原始 {@link ToolCallback} 执行，参数、元数据、执行逻辑完全透传。
 * </p>
 * <p>
 * 行为说明：
 * <ul>
 * <li>{@link #getToolDefinition()}：返回拼接命名空间后的新ToolDefinition（对外给模型使用）；</li>
 * <li>{@link #getToolMetadata()}：直接透传原始工具元数据，不做修改；</li>
 * <li>{@link #call} 两个重载：全部直接委托原始delegate执行，入参出参原样透传；</li>
 * <li>构造时校验拼接后的工具名称长度，超出模型工具名最大长度限制直接抛出异常，避免传给大模型产生契约错误。</li>
 * </ul>
 * </p>
 * <p>
 * 使用场景：同一个Agent任务会话下接入多个MCP Server，不同Server存在同名工具；
 * 通过serverKey做命名空间隔离，大模型侧看到的工具全局唯一，内部路由仍然交给原始MCP工具回调处理。
 * </p>
 */
public final class NamespacedToolCallback implements ToolCallback {
    /**
     * 被包装的原始工具回调，真实执行工具call逻辑
     */
    private final ToolCallback delegate;
    /**
     * 命名空间处理后的工具定义：name = serverKey__原始工具名；
     * 该definition对外提供给LLM，用于模型函数调用；description、inputSchema复用原始工具信息。
     */
    private final ToolDefinition definition;

    /**
     * 构造命名空间包装回调
     * @param delegate 原始MCP/Skill工具回调实例
     * @param serverKey MCP服务唯一标识key，作为命名空间前缀；拼接格式：serverKey__toolName
     * @throws IllegalStateException 拼接完成后的工具名称超出模型允许最大工具名长度时抛出
     */
    public NamespacedToolCallback(ToolCallback delegate, String serverKey) {
        this.delegate = delegate;
        // 获取原始工具定义
        ToolDefinition original = delegate.getToolDefinition();
        // 拼接命名空间，双下划线作为分隔符
        String name = serverKey + "__" + original.name();
        // 校验命名空间后的工具名长度，防止下发给大模型出现参数非法
        if (name.length() > McpConstant.MAX_MODEL_TOOL_NAME_LENGTH) {
            throw new IllegalStateException("命名空间化 MCP 工具名超过长度限制");
        }
        // 构建新的ToolDefinition：仅替换name，描述、入参schema完全复用原始工具
        this.definition = ToolDefinition.builder()
                .name(name)
                .description(original.description())
                .inputSchema(original.inputSchema())
                .build();
    }

    /**
     * 获取对外暴露给大模型的工具定义（已经带上serverKey命名空间前缀）
     * @return 命名空间化后的工具定义
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    /**
     * 获取工具元数据，直接透传原始工具元数据，不做命名空间修改
     * @return 原始工具元数据
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * 无上下文工具调用：直接委托原始工具执行，入参原样透传
     * @param input 工具调用入参字符串
     * @return 原始工具返回结果
     */
    @Override
    public String call(String input) {
        return delegate.call(input);
    }

    /**
     * 携带ToolContext上下文工具调用：直接委托原始工具执行，入参、上下文原样透传
     * @param input 工具调用入参字符串
     * @param context 工具运行上下文
     * @return 原始工具返回结果
     */
    @Override
    public String call(String input, ToolContext context) {
        return delegate.call(input, context);
    }
}
