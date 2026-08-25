package com.agentdoc.agent.execution.model;

/**
 * 模型能力描述对象
 * <p>
 * 声明模型适配器具备的能力集合，用于运行时做任务能力校验。
 * 在{@link ModelAdapterRegistry#require(ModelEntity, ModelCapabilities)}执行能力匹配，
 * Agent执行工具循环时会校验模型是否支持工具调用。
 * </p>
 * @param toolCalling 是否支持工具调用（function‑call）
 * @param parallelToolCalling 是否支持并行工具调用，一次返回多个toolCall
 */
public record ModelCapabilities(
        // 是否支持工具调用（function‑call）
        boolean toolCalling,
        // 是否支持并行工具调用，一次返回多个toolCall
        boolean parallelToolCalling) {

    /**
     * 构造任务需要的最小能力：必须支持工具调用，用于Agent工具循环任务
     * @return 要求具备工具调用能力的能力对象
     */
    public static ModelCapabilities requiredToolCalling() {
        return new ModelCapabilities(true, false);
    }

    /**
     * 判断当前适配器能力是否满足任务的要求能力
     * @param required 任务所需要的最低能力
     * @return true：全部能力满足；false：存在能力缺失
     */
    public boolean supports(ModelCapabilities required) {
        return (!required.toolCalling() || toolCalling)
                && (!required.parallelToolCalling() || parallelToolCalling);
    }
}
