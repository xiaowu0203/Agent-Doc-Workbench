package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 审计日志目标类型。
 */
public enum AuditTargetType {

    AGENT("agent"),
    TASK("task"),
    CHANGE_REQUEST("change_request"),
    DOCUMENT_VERSION("document_version"),
    SPACE_ROLE("space_role");

    @Schema(description = "审计目标类型编码")
    private final String code;

    AuditTargetType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
