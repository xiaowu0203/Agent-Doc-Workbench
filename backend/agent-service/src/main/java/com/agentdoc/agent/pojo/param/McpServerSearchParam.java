package com.agentdoc.agent.pojo.param;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.agentdoc.agent.constant.McpConstant.MAX_SEARCH_KEYWORD_LENGTH;

/**
 * MCP Server 分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP Server 分页查询参数")
public class McpServerSearchParam extends PageParam {

    @NotNull
    @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long spaceId;

    @Min(0)
    @Max(1)
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;

    @Schema(description = "认证类型")
    private McpAuthType authType;

    @Size(max = MAX_SEARCH_KEYWORD_LENGTH)
    @Schema(description = "技术标识或展示名称关键字")
    private String keyword;
}
