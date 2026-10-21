package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record TaskNameVO(
        @Schema(description = "任务ID") Long id,
        @Schema(description = "所属空间ID") Long spaceId,
        @Schema(description = "当前任务名称") String name) {
}
