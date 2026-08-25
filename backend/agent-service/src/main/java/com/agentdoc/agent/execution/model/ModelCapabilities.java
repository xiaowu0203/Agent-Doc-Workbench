package com.agentdoc.agent.execution.model;

/** 模型适配器声明的能力集合。 */
public record ModelCapabilities(
        boolean toolCalling,
        boolean parallelToolCalling) {

    public static ModelCapabilities requiredToolCalling() {
        return new ModelCapabilities(true, false);
    }

    public boolean supports(ModelCapabilities required) {
        return (!required.toolCalling() || toolCalling)
                && (!required.parallelToolCalling() || parallelToolCalling);
    }
}
