package com.agentdoc.common.enums;

/**
 * 任务执行副作用模式。
 * <p>
 * 控制本次Agent执行是否允许产生真实外部副作用，用于A2A快照预计算与正式执行分流。
 */
public enum TaskExecutionMode {
    /**
     * 实时执行模式：调用MCP、工作台写工具时执行真实逻辑，产生文档变更、外部调用等副作用。
     */
    LIVE,
    /**
     * 隔离捕获模式：禁止外部MCP连接；工作台写工具仅捕获入参、保存候选产物artifact，
     * 不修改真实文档，目的是生成稳定执行快照与哈希，用于签发能力令牌。
     */
    ISOLATED
}