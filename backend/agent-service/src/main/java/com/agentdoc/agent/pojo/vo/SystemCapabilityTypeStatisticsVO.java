package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.SystemCapabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "单类系统能力统计")
public record SystemCapabilityTypeStatisticsVO(
        @Schema(description = "能力类型") SystemCapabilityType type,
        @Schema(description = "能力总数") Long totalCount,
        @Schema(description = "启用能力数") Long enabledCount,
        @Schema(description = "已发布版本数") Long publishedVersionCount,
        @Schema(description = "空间安装实例数") Long installationCount) {
}
