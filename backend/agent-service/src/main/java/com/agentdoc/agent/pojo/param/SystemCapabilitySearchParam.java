package com.agentdoc.agent.pojo.param;

import com.agentdoc.agent.enums.SystemCapabilityType;
import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统能力目录搜索参数")
public class SystemCapabilitySearchParam extends PageParam {
    @Schema(description = "能力类型；为空时查询全部类型")
    private SystemCapabilityType type;
    @Min(0)
    @Max(1)
    @Schema(description = "状态：0 停用 / 1 启用；仅平台超级管理员生效")
    private Integer status;
    @Size(max = 100)
    @Schema(description = "技术标识、展示名称或说明关键词")
    private String keyword;
}
