package com.agentdoc.document.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档类型（草稿 / 正式双文档模式）。
 * <p>草稿：Agent 可直改免审批；正式：所有 Agent 修改必须经 Diff 审批合并，Agent 无直接写入权限。</p>
 */
@Schema(description = "文档类型")
public enum DocType {

    /** 正式文档：成果沉淀专用，Agent 修改必须审批合并 */
    FORMAL(1, "正式"),

    /** 草稿文档：试错专用，Agent 可自由编辑免审批 */
    DRAFT(2, "草稿");

    private final int code;
    private final String name;

    DocType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按数据库编码解析，未知编码返回 null。
     * @param code 数据库 doc_type 字段值
     * @return 对应枚举，未知返回 null
     */
    public static DocType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocType type : values()) {
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
