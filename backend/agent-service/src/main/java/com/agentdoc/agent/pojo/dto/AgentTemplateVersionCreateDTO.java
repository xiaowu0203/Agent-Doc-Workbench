package com.agentdoc.agent.pojo.dto;

import com.agentdoc.agent.enums.SkillSelectionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

@Schema(description = "Agent 模板版本创建参数")
public record AgentTemplateVersionCreateDTO(
        @NotBlank @Size(max = 100) @Schema(description = "版本默认展示名称") String displayName,
        @Size(max = 500) @Schema(description = "版本说明") String description,
        @NotBlank @Schema(description = "默认系统提示词") String systemPrompt,
        @NotNull @Schema(description = "默认模型 ID") Long modelId,
        @NotNull @Schema(description = "默认 Skill 选择模式") SkillSelectionMode skillSelectionMode,
        @Schema(description = "默认 Skill Router 模型 ID") Long skillRouterModelId,
        @NotNull @Schema(description = "默认是否启用外部 MCP") Boolean externalMcpEnabled,
        @Min(MIN_TOKEN_BUDGET) @Schema(description = "默认 Token 预算") Long tokenBudget,
        @Size(max = MAX_TOOL_WHITELIST_SIZE)
        @Schema(description = "默认模型工具白名单")
        List<@NotBlank @Size(max = MAX_MODEL_TOOL_NAME_LENGTH) String> toolWhitelist,
        @Min(MIN_MAX_ITERATIONS) @Max(MAX_MAX_ITERATIONS)
        @Schema(description = "默认最大迭代次数") Integer maxIterations,
        @Min(MIN_EXECUTION_TIMEOUT_SECONDS) @Max(MAX_EXECUTION_TIMEOUT_SECONDS)
        @Schema(description = "默认执行超时秒数") Integer executionTimeoutSeconds,
        @Valid @Size(max = 20) @Schema(description = "固定的系统 Skill 版本引用")
        List<@NotNull @Valid AgentTemplateSkillDTO> skills) { }
