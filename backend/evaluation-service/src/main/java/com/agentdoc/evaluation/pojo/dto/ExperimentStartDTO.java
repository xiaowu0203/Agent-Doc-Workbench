package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 启动或恢复 Experiment 的预算确认参数。 */
public record ExperimentStartDTO(
        @NotNull @Min(1) Long authorizedTokenBudget,
        @NotNull @Min(60) Long workerCapabilityTtlSeconds) {
}
