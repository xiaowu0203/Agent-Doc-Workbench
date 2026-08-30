package com.agentdoc.agent.pojo.dto;

import com.agentdoc.agent.enums.SkillSelectionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.agent.constant.AgentConstant.MAX_EXECUTION_TIMEOUT_SECONDS;
import static com.agentdoc.agent.constant.AgentConstant.MAX_MAX_ITERATIONS;
import static com.agentdoc.agent.constant.AgentConstant.MIN_EXECUTION_TIMEOUT_SECONDS;
import static com.agentdoc.agent.constant.AgentConstant.MIN_MAX_ITERATIONS;
import static com.agentdoc.agent.constant.AgentConstant.MIN_TOKEN_BUDGET;
import static com.agentdoc.agent.constant.McpConstant.MAX_MODEL_TOOL_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_TOOL_WHITELIST_SIZE;

@Schema(description = "Agent 创建参数")
public record AgentCreateDTO(
        @NotNull @Schema(description = "空间 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long spaceId,
        @NotBlank @Schema(description = "Agent 名称", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "Agent 描述") String description,
        @NotBlank @Schema(description = "系统提示词", requiredMode = Schema.RequiredMode.REQUIRED) String systemPrompt,
        @NotNull @Schema(description = "模型 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long modelId,
        @NotNull @Schema(description = "Skill 选择模式", requiredMode = Schema.RequiredMode.REQUIRED)
        SkillSelectionMode skillSelectionMode,
        @Schema(description = "Skill Router 模型 ID；为空时复用 Agent 主模型") Long skillRouterModelId,
        @NotNull @Schema(description = "是否启用外部 MCP", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean externalMcpEnabled,
        @Min(MIN_TOKEN_BUDGET) @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "文档访问范围") String documentScope,
        @Schema(description = "模型工具白名单；null 表示不额外限制，空数组表示禁用全部工具")
        @Size(max = MAX_TOOL_WHITELIST_SIZE)
        List<@NotBlank @Size(max = MAX_MODEL_TOOL_NAME_LENGTH) String> toolWhitelist,
        @Min(MIN_MAX_ITERATIONS) @Max(MAX_MAX_ITERATIONS)
        @Schema(description = "最大工具迭代次数") Integer maxIterations,
        @Min(MIN_EXECUTION_TIMEOUT_SECONDS) @Max(MAX_EXECUTION_TIMEOUT_SECONDS)
        @Schema(description = "执行超时时间（秒）")
        Integer executionTimeoutSeconds) {
}
