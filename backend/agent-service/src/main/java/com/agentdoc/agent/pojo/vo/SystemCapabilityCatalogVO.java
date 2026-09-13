package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.SystemCapabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "统一系统能力目录项")
public record SystemCapabilityCatalogVO(
        @Schema(description = "能力 ID") Long id,
        @Schema(description = "能力类型") SystemCapabilityType type,
        @Schema(description = "稳定技术标识") String technicalKey,
        @Schema(description = "展示名称") String displayName,
        @Schema(description = "说明") String description,
        @Schema(description = "状态：0 停用 / 1 启用") Integer status,
        @Schema(description = "最新已发布版本 ID；尚无已发布版本时为空") Long latestPublishedVersionId,
        @Schema(description = "最新已发布版本号；尚无已发布版本时为空") Integer latestPublishedVersionNo,
        @Schema(description = "空间安装实例数") Long installationCount,
        @Schema(description = "Skill 版本数；非 Skill 为空") Long skillVersionCount,
        @Schema(description = "绑定启用 Agent 数；非 Skill 为空") Long skillBoundAgentCount,
        @Schema(description = "Skill 最新版本号；非 Skill 为空") Integer latestSkillVersionNo,
        @Schema(description = "Skill 最新版本状态；非 Skill 为空") Integer latestSkillVersionStatus,
        @Schema(description = "Skill 最新版本激活说明；非 Skill 为空") String latestSkillActivationDescription,
        @Schema(description = "Skill 最新版本允许工具数；非 Skill 为空") Integer latestSkillAllowedToolCount,
        @Schema(description = "Skill 最新版本创建时间；非 Skill 为空") LocalDateTime latestSkillVersionCreatedAt,
        @Schema(description = "Skill 最新版本发布时间；非 Skill 为空") LocalDateTime latestSkillVersionPublishedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {
}
