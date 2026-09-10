package com.agentdoc.agent.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统 Agent 模板查询参数")
public class AgentTemplateSearchParam extends PageParam {
    @Min(0)
    @Max(1)
    @Schema(description = "状态：0 停用 / 1 启用")
    private Integer status;
    @Size(max = 100)
    @Schema(description = "名称或说明关键词")
    private String keyword;
}
