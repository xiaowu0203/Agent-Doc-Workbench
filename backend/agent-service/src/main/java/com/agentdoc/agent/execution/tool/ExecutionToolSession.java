package com.agentdoc.agent.execution.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Agent单次任务执行‑工具会话
 * <p>
 * 绑定单次Agent任务执行完整生命周期，属于任务作用域对象。
 * 1. 持有当前任务下所有任务级MCP工具会话实例，MCP会话与Agent任务同生命周期；
 * 2. 持有工具回调链集合（审计回调、事件回调等），工具执行时会使用这批回调；
 * 3. 实现 {@link AutoCloseable}，配合 try‑with‑resources 语法；任务正常结束/异常终止时，自动关闭、释放所有MCP会话资源，避免MCP连接泄漏。
 * </p>
 * </p>
 */
public final class ExecutionToolSession implements AutoCloseable {
    /**
     * 当前Agent任务作用域下的MCP工具会话集合；
     * 每个元素代表一个任务级MCP客户端会话，任务结束必须close释放底层连接；不可修改。
     */
    private final List<TaskScopedMcpTools> mcpSessions;
    /**
     * 工具调用回调集合；
     * 存放包装后的ToolCallback实现，例如审计包装回调 {@link AuditingToolCallback}、事件通知回调等；
     * 工具执行链路会使用该列表，内部集合不可修改。
     */
    private final List<ToolCallback> callbacks;

    /**
     * 构造任务工具执行会话
     * @param mcpSessions 任务级MCP工具会话列表，会话生命周期跟随本次Agent任务，close时统一释放；入参集合会做不可变拷贝
     * @param callbacks 工具调用回调集合，不能为null；入参集合会做不可变拷贝
     */
    public ExecutionToolSession(List<TaskScopedMcpTools> mcpSessions, List<ToolCallback> callbacks) {
        // List.copyOf：拷贝为不可变集合，防止外部持有原列表后续add/remove篡改会话内部状态
        this.mcpSessions = List.copyOf(mcpSessions);
        this.callbacks = List.copyOf(callbacks);
    }

    /**
     * 获取工具回调只读列表
     * @return 工具调用回调集合，返回不可变视图
     */
    public List<ToolCallback> callbacks() {
        return callbacks;
    }

    /**
     * 关闭工具会话，释放全部MCP会话资源
     * <p>
     * 使用 {@code try‑with‑resources} 会自动调用本方法；任务执行完成、任务异常终止均需要执行close。
     * 关闭逻辑：
     * <ol>
     * <li>倒序关闭MCP会话：后创建的会话优先关闭；</li>
     * <li>逐个捕获每个session.close()抛出的运行时异常；第一个异常作为主异常，其余异常使用 addSuppressed() 追加抑制异常；</li>
     * <li>全部会话执行关闭完成后，如果存在关闭异常，则抛出聚合后的异常；保证尽可能释放全部资源，不会单个会话异常就中断循环。</li>
     * </ol>
     * </p>
     * @throws RuntimeException MCP会话关闭过程中产生异常时抛出，多个关闭异常会以suppressed形式聚合
     */
    @Override
    public void close() {
        RuntimeException failure = null;
        // reversed() 倒序关闭：遵循“后创建先释放”资源释放习惯
        for (TaskScopedMcpTools session : mcpSessions.reversed()) {
            try {
                session.close();
            } catch (RuntimeException exception) {
                if (failure == null)
                    // 记录第一个发生的异常作为主异常
                    failure = exception;
                else
                    // 后续关闭异常作为抑制异常附加到主异常上，不丢失任何关闭错误信息
                    failure.addSuppressed(exception);
            }
        }
        // 只要存在关闭异常，向上抛出聚合异常，通知上层任务执行失败
        if (failure != null)
            throw failure;
    }
}
