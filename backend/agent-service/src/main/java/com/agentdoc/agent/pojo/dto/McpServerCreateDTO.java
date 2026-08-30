package com.agentdoc.agent.pojo.dto;

import com.agentdoc.agent.enums.McpAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.agentdoc.agent.constant.McpConstant.MAX_AUTH_TOKEN_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_DISPLAY_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_ENDPOINT_URL_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_SERVER_KEY_LENGTH;

@Schema(description = "MCP Server 创建参数")
public record McpServerCreateDTO(
        @Schema(description = "所属空间 ID") @NotNull Long spaceId,
        @Schema(description = "空间内唯一的 MCP 技术标识")
        @NotBlank @Size(max = MAX_SERVER_KEY_LENGTH)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String serverKey,
        @Schema(description = "MCP Server 展示名称")
        @NotBlank @Size(max = MAX_DISPLAY_NAME_LENGTH) String displayName,
        @Schema(description = "公网 HTTPS Streamable HTTP 端点")
        @NotBlank @Size(max = MAX_ENDPOINT_URL_LENGTH) String endpointUrl,
        @Schema(description = "认证类型")
        @NotNull McpAuthType authType,
        @Schema(description = "认证令牌，只写不回显", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = MAX_AUTH_TOKEN_LENGTH) String authToken) {
    @Override
    public String toString() {
        return "McpServerCreateDTO[spaceId=" + spaceId + ", serverKey=" + serverKey
                + ", displayName=" + displayName + ", endpointUrl=" + endpointUrl
                + ", authType=" + authType + ", authToken=<redacted>]";
    }
}
