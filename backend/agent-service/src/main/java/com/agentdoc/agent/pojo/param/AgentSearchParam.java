package com.agentdoc.agent.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.agentdoc.agent.constant.AgentConstant.MAX_SEARCH_KEYWORD_LENGTH;

/**
 * Agent 分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Agent 分页查询参数")
public class AgentSearchParam extends PageParam {

    @NotNull
    @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long spaceId;

    @Min(0)
    @Max(1)
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;

    @Schema(description = "模型 ID")
    private Long modelId;

    @Size(max = MAX_SEARCH_KEYWORD_LENGTH)
    @Schema(description = "Agent 名称或描述关键字")
    private String keyword;
}
