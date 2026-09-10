package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Skill 启用绑定数量")
public class SkillBindingCountVO {

    @Schema(description = "Skill ID")
    private Long skillId;

    @Schema(description = "当前启用绑定的 Agent 数量")
    private Long boundAgentCount;
}
