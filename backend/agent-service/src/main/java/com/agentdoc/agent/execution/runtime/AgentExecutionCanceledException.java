package com.agentdoc.agent.execution.runtime;

/**
 * Agent任务执行取消异常
 * <p>
 * 用于中断Agent Runtime执行流程，代表用户主动取消任务、外部触发终止。
 * 属于业务控制异常，不是系统错误；上层捕获后停止Agent循环，返回取消状态，不需要计入错误告警。
 * </p>
 */
public class AgentExecutionCanceledException extends RuntimeException {

    /**
     * 构造任务已取消异常，默认提示信息：任务已取消
     */
    public AgentExecutionCanceledException() {
        super("任务已取消");
    }
}
