package com.agentdoc.agent.pojo.dto;

import com.agentdoc.agent.enums.McpAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.agentdoc.agent.constant.McpConstant.MAX_AUTH_PARAM_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_DISPLAY_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_ENDPOINT_URL_LENGTH;

@Schema(description = "系统 MCP 模板更新参数")
public record McpTemplateUpdateDTO(
        @NotBlank @Size(max = MAX_DISPLAY_NAME_LENGTH)
        @Schema(description = "展示名称") String displayName,
        @Size(max = 500) @Schema(description = "模板说明") String description,
        @NotBlank @Size(max = MAX_ENDPOINT_URL_LENGTH)
        @Schema(description = "默认公网 HTTPS Streamable HTTP 端点") String endpointUrl,
        @NotNull @Schema(description = "认证类型") McpAuthType authType,
        @Size(max = MAX_AUTH_PARAM_NAME_LENGTH)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]*$")
        @Schema(description = "Query API Key 参数名；QUERY_PARAM 认证时必填") String authParamName,
        @NotNull @Min(0) @Max(1) @Schema(description = "状态：0 停用 / 1 启用") Integer status) {
}
