package com.agentdoc.agent.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Skill 查询参数")
public class SkillSearchParam extends PageParam {

    @Schema(description = "空间 ID")
    private Long spaceId;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "名称关键字")
    private String keyword;
}
