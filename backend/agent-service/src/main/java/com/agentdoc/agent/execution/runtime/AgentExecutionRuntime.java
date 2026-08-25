package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Agent执行运行时接口
 * <p>
 * Agent任务执行入口SPI，定义同步执行契约；
 * 接收Agent配置、模型配置、用户指令、任务入参、取消信号，返回完整执行结果。
 * </p>
 * <p>
 * 重载default方法支持传入文本增量回调onTextDelta，用于流式输出；
 * 默认实现忽略增量回调，子类实现可覆写该方法完成流式推送。
 * </p>
 */
public interface AgentExecutionRuntime {

    /**
     * 同步执行Agent任务
     * @param agent Agent实体，包含角色设定、工具集配置等
     * @param model 使用的模型实体
     * @param instruction 用户顶层指令
     * @param input 任务额外输入DTO
     * @param cancelRequested 取消信号判断器，返回true代表需要终止任务
     * @return Agent完整执行结果 {@link AgentRuntimeResult}
     */
    AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                               AgentTaskInputDTO input, BooleanSupplier cancelRequested);

    /**
     * 带文本流式增量回调的执行重载
     * @param agent Agent实体
     * @param model 使用的模型实体
     * @param instruction 用户顶层指令
     * @param input 任务额外输入DTO
     * @param cancelRequested 取消信号判断器
     * @param onTextDelta 文本增量回调，每产生一段输出文本会回调该Consumer，用于向前端推送流式片段
     * @return Agent完整执行结果
     */
    default AgentRuntimeResult execute(AgentEntity agent, ModelEntity model, String instruction,
                                       AgentTaskInputDTO input, BooleanSupplier cancelRequested,
                                       Consumer<String> onTextDelta) {
        return execute(agent, model, instruction, input, cancelRequested);
    }
}
