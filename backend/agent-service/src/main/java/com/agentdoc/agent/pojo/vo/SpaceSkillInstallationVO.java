package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "空间系统 Skill 安装信息")
public record SpaceSkillInstallationVO(
        @Schema(description = "安装记录 ID") Long id,
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "系统 Skill ID") Long skillId,
        @Schema(description = "Skill 名称") String skillName,
        @Schema(description = "Skill 展示名称") String displayName,
        @Schema(description = "Skill 描述") String description,
        @Schema(description = "当前固定版本 ID") Long skillVersionId,
        @Schema(description = "当前固定版本号") Integer versionNo,
        @Schema(description = "系统当前最新已发布版本 ID") Long latestPublishedVersionId,
        @Schema(description = "系统当前最新已发布版本号") Integer latestPublishedVersionNo,
        @Schema(description = "是否存在可升级版本") boolean upgradeAvailable,
        @Schema(description = "是否启用") Boolean enabled,
        @Schema(description = "安装人用户 ID") Long installedBy,
        @Schema(description = "安装时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {
}
