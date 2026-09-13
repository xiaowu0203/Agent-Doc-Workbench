package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_template_skill")
@Schema(description = "Agent 模板版本的系统 Skill 引用")
public class AgentTemplateSkillEntity extends BaseEntity {
    @Schema(description = "模板版本 ID") private Long templateVersionId;
    @Schema(description = "系统 Skill ID") private Long skillId;
    @Schema(description = "固定 Skill 版本 ID") private Long skillVersionId;
}
