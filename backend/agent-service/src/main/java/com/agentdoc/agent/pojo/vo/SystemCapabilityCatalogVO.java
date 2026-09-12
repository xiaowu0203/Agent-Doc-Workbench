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
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {
}
