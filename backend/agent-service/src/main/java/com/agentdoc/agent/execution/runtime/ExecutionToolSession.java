package com.agentdoc.agent.execution.runtime;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Agent单次任务执行‑工具会话
 * <p>
 * 绑定一次Agent执行生命周期，持有任务作用域MCP工具实例与工具回调集合；
 * 实现{@link AutoCloseable}，任务结束时自动释放MCP工具会话资源。
 * </p>
 */
public final class ExecutionToolSession implements AutoCloseable {
    /** 任务作用域MCP工具客户端会话 */
    private final TaskScopedMcpTools mcpTools;
    /** 工具调用回调监听器列表，用于收集工具调用日志、审计、事件通知 */
    private final List<ToolCallback> callbacks;

    /**
     * 构造工具执行会话
     *
     * @param mcpTools   任务级MCP工具实例，任务结束需要关闭释放
     * @param callbacks  工具调用回调集合，不可为null
     */
    public ExecutionToolSession(TaskScopedMcpTools mcpTools, List<ToolCallback> callbacks) {
        this.mcpTools = mcpTools;
        this.callbacks = callbacks;
    }

    /**
     * 获取工具调用回调列表
     *
     * @return 回调监听器集合
     */
    public List<ToolCallback> callbacks() {
        return callbacks;
    }

    /**
     * 关闭会话，释放MCP工具相关资源
     * <p>任务执行完成/异常终止后调用，安全空判断，重复close无害</p>
     */
    @Override
    public void close() {
        if (mcpTools != null) {
            mcpTools.close();
        }
    }
}
