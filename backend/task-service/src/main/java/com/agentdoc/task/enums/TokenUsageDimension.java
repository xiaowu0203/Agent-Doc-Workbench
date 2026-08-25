package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * Token 用量聚合维度。
 */
@Getter
public enum TokenUsageDimension {

    SPACE(1, "空间"),
    DOCUMENT(2, "文档"),
    TASK(3, "任务"),
    AGENT(4, "Agent");

    @Schema(description = "聚合维度编码")
    private final int code;

    @Schema(description = "聚合维度名称")
    private final String name;

    TokenUsageDimension(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
