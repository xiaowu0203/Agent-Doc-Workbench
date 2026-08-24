package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 业务操作主体类型。
 */
@Getter
public enum ActorType {

    HUMAN(1, "用户"),
    AGENT(2, "Agent");

    @Schema(description = "主体类型编码")
    private final int code;

    @Schema(description = "主体类型名称")
    private final String name;

    ActorType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
