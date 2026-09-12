package com.agentdoc.agent.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统 Skill 目录查询参数")
public class SystemSkillSearchParam extends PageParam {

    @Schema(description = "状态；仅平台超级管理员可查询停用项")
    private Integer status;

    @Schema(description = "名称关键字")
    private String keyword;
}
