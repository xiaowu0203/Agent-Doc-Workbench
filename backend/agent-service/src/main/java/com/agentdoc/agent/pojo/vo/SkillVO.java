package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.SkillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Skill 信息")
public record SkillVO(
        @Schema(description = "Skill ID") Long id,
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "状态") SkillStatus status,
        @Schema(description = "当前版本数量") long versionCount,
        @Schema(description = "创建人") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
