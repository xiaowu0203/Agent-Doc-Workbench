package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.SkillVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Skill 版本信息")
public record SkillVersionVO(
        @Schema(description = "版本 ID") Long id,
        @Schema(description = "Skill ID") Long skillId,
        @Schema(description = "版本号") Integer versionNo,
        @Schema(description = "状态") SkillVersionStatus status,
        @Schema(description = "版本级激活描述") String activationDescription,
        @Schema(description = "SHA-256") String sha256,
        @Schema(description = "ZIP 大小") Long packageSize,
        @Schema(description = "工具白名单") List<String> allowedTools,
        @Schema(description = "资源清单") List<String> readableResourcePaths,
        @Schema(description = "创建人") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "发布时间") LocalDateTime publishedAt) {
}
