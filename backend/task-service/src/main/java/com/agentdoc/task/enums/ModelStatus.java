package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 模型启停状态。
 */
@Getter
public enum ModelStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    @Schema(description = "模型状态码")
    private final int code;

    @Schema(description = "模型状态名称")
    private final String name;

    ModelStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static ModelStatus fromCode(Integer code) {
        for (ModelStatus status : values()) {
            if (status.code == (code == null ? 0 : code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知模型状态码：" + code);
    }
}
