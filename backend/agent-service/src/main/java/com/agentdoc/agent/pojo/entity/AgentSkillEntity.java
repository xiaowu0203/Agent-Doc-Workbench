package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill")
@Schema(description = "Agent Skill 当前绑定")
public class AgentSkillEntity extends BaseEntity {

    @Schema(description = "Agent ID")
    private Long agentId;
    @Schema(description = "Skill ID")
    private Long skillId;
    @Schema(description = "Skill 版本 ID")
    private Long skillVersionId;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
