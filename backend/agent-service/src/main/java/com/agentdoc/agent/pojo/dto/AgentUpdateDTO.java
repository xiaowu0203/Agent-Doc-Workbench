package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import static com.agentdoc.agent.constant.AgentConstant.MAX_EXECUTION_TIMEOUT_SECONDS;
import static com.agentdoc.agent.constant.AgentConstant.MAX_MAX_ITERATIONS;
import static com.agentdoc.agent.constant.AgentConstant.MIN_EXECUTION_TIMEOUT_SECONDS;
import static com.agentdoc.agent.constant.AgentConstant.MIN_MAX_ITERATIONS;
import static com.agentdoc.agent.constant.AgentConstant.MIN_TOKEN_BUDGET;

@Schema(description = "Agent 更新参数")
public record AgentUpdateDTO(
        @NotBlank @Schema(description = "Agent 名称", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "Agent 描述") String description,
        @NotBlank @Schema(description = "系统提示词", requiredMode = Schema.RequiredMode.REQUIRED) String systemPrompt,
        @NotNull @Schema(description = "模型 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long modelId,
        @Min(MIN_TOKEN_BUDGET) @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "文档访问范围") String documentScope,
        @Min(MIN_MAX_ITERATIONS) @Max(MAX_MAX_ITERATIONS)
        @Schema(description = "最大工具迭代次数") Integer maxIterations,
        @Min(MIN_EXECUTION_TIMEOUT_SECONDS) @Max(MAX_EXECUTION_TIMEOUT_SECONDS)
        @Schema(description = "执行超时时间（秒）")
        Integer executionTimeoutSeconds,
        @NotNull @Schema(description = "状态：0 禁用 / 1 启用", requiredMode = Schema.RequiredMode.REQUIRED) Integer status) {
}
