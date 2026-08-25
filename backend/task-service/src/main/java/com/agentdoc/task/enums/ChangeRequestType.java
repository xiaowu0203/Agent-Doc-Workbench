package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 变更请求类型（对应目标文档的正式 / 草稿模式）。
 */
@Schema(description = "变更请求类型")
public enum ChangeRequestType {

    /** 正式文档：变更需审批合并 */
    FORMAL(1, "正式"),

    /** 草稿文档：Agent 可直写免审批 */
    DRAFT(2, "草稿");

    private final int code;
    private final String name;

    ChangeRequestType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按数据库编码解析，未知编码返回 null。
     * @param code 数据库 request_type 字段值
     * @return 对应枚举，未知返回 null
     */
    public static ChangeRequestType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ChangeRequestType type : values()) {
            if (type.code == code) {
                return type;
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
