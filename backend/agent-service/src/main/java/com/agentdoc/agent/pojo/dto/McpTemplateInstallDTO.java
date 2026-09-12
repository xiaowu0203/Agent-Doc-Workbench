package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.agentdoc.agent.constant.McpConstant.MAX_AUTH_TOKEN_LENGTH;

@Schema(description = "系统 MCP 模板空间安装参数")
public record McpTemplateInstallDTO(
        @NotNull @Schema(description = "已发布 MCP 模板版本 ID") Long templateVersionId,
        @Size(max = MAX_AUTH_TOKEN_LENGTH)
        @Schema(description = "当前空间独立凭证；NONE 认证无需填写", accessMode = Schema.AccessMode.WRITE_ONLY)
        String authToken) {
    @Override
    public String toString() {
        return "McpTemplateInstallDTO[templateVersionId=" + templateVersionId + ", authToken=<redacted>]";
    }
}
