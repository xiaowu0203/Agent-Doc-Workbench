package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "系统 Agent 模板创建参数")
public record AgentTemplateCreateDTO(
        @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 100)
        @Schema(description = "稳定技术名称") String name,
        @NotBlank @Size(max = 100) @Schema(description = "展示名称") String displayName,
        @Size(max = 500) @Schema(description = "模板说明") String description) { }
