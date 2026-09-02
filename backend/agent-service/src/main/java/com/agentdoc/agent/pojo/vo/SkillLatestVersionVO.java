package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.SkillVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Skill 最新版本摘要")
public record SkillLatestVersionVO(
        @Schema(description = "版本 ID") Long id,
        @Schema(description = "版本号") Integer versionNo,
        @Schema(description = "状态") SkillVersionStatus status,
        @Schema(description = "版本级激活描述") String activationDescription,
        @Schema(description = "声明工具数量") int allowedToolCount,
        @Schema(description = "上传时间") LocalDateTime createdAt,
        @Schema(description = "发布时间") LocalDateTime publishedAt) {
}
