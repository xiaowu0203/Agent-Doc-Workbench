package com.agentdoc.agent.execution.runtime;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Agent执行运行时接口
 * <p>
 * 定义Agent任务执行契约；接收已经完成准备、参数固化后的运行时上下文，
 * 支持同步阻塞执行，同时提供流式增量输出的可选重载；
 * 通过取消信号支持任务中断控制。
 * </p>
 */
public interface AgentExecutionRuntime {

    /**
     * 同步执行Agent任务，使用已经准备完成的运行时上下文
     * 该方法会阻塞执行直到任务完成或被取消
 *
     * @param context        Agent运行时上下文，已完成配置、工具、Skill等参数准备，执行过程中不允许修改
     * @param cancelRequested 取消信号断言，轮询判断任务是否需要被中断；返回true表示应当终止执行
     * @return Agent执行最终结果对象，包含输出、工具调用记录、token统计、终止原因等信息
     */
    AgentRuntimeResult execute(AgentRuntimeContext context, BooleanSupplier cancelRequested);

    /**
     * 同步执行Agent任务，同时向外推送文本增量流式回调
     * <p>默认实现直接委托同步执行，忽略增量回调；子类可重写实现流式输出能力</p>
     *
     * @param context        Agent运行时上下文，已完成配置、工具、Skill等参数准备，执行过程中不允许修改
     * @param cancelRequested 取消信号断言，轮询判断任务是否需要被中断；返回true表示应当终止执行
     * @param onTextDelta    文本增量回调，接收模型实时输出片段；null则不触发回调
     * @return Agent执行最终结果对象，包含输出、工具调用记录、token统计、终止原因等信息
     */
    default AgentRuntimeResult execute(AgentRuntimeContext context, BooleanSupplier cancelRequested,
                                       Consumer<String> onTextDelta) {
        return execute(context, cancelRequested);
    }
}
