package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * Token 用量快照类型。
 */
@Getter
public enum TokenSnapshotType {

    SYSTEM(1, "系统自动"),
    MANUAL(2, "用户手动");

    @Schema(description = "快照类型编码")
    private final int code;

    @Schema(description = "快照类型名称")
    private final String name;

    TokenSnapshotType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}
