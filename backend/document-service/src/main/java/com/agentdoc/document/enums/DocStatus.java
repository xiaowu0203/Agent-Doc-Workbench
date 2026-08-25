package com.agentdoc.document.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档状态。
 */
@Schema(description = "文档状态")
public enum DocStatus {

    /** 正常 */
    NORMAL(1, "正常"),

    /** 已归档（回收站） */
    ARCHIVED(0, "已归档");

    private final int code;
    private final String name;

    DocStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按数据库编码解析，未知编码返回 null。
     * @param code 数据库 status 字段值
     * @return 对应枚举，未知返回 null
     */
    public static DocStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
