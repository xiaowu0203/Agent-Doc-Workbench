package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Agent 模板安装参数")
public record AgentTemplateInstallDTO(
        @NotNull @Schema(description = "已发布模板版本 ID") Long templateVersionId,
        @Size(max = 100) @Schema(description = "空间 Agent 名称覆盖；为空使用模板名称") String name,
        @Schema(description = "空间文档范围，不从系统模板继承") String documentScope) { }
