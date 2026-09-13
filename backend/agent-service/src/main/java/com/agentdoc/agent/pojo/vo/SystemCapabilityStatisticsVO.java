package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "系统能力中心平台统计")
public record SystemCapabilityStatisticsVO(
        @Schema(description = "系统能力总数") long totalCount,
        @Schema(description = "启用能力总数") long enabledCount,
        @Schema(description = "已发布版本总数") long publishedVersionCount,
        @Schema(description = "空间安装实例总数") long installationCount,
        @Schema(description = "按能力类型统计") List<SystemCapabilityTypeStatisticsVO> byType) {
}
