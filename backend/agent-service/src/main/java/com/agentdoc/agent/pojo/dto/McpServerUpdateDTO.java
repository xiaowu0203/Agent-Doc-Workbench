package com.agentdoc.agent.pojo.dto;

import com.agentdoc.agent.enums.McpAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.agentdoc.agent.constant.McpConstant.MAX_AUTH_TOKEN_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_DISPLAY_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_ENDPOINT_URL_LENGTH;

@Schema(description = "MCP Server 更新参数")
public record McpServerUpdateDTO(
        @Schema(description = "MCP Server 展示名称")
        @NotBlank
        @Size(max = MAX_DISPLAY_NAME_LENGTH)
        String displayName,

        @Schema(description = "公网 HTTPS Streamable HTTP 端点")
        @NotBlank
        @Size(max = MAX_ENDPOINT_URL_LENGTH)
        String endpointUrl,

        @Schema(description = "认证类型")
        @NotNull
        McpAuthType authType,

        @Schema(description = "新认证令牌；为空表示保留已有令牌", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = MAX_AUTH_TOKEN_LENGTH)
        String authToken,

        @Schema(description = "状态：0 禁用 / 1 启用")
        @NotNull
        @Min(0)
        @Max(1)
        Integer status) {
    @Override
    public String toString() {
        return "McpServerUpdateDTO[displayName=" + displayName + ", endpointUrl=" + endpointUrl
                + ", authType=" + authType + ", authToken=<redacted>, status=" + status + "]";
    }
}
