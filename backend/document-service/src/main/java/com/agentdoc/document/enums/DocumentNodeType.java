package com.agentdoc.document.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档树节点类型。
 */
@Schema(description = "文档树节点类型")
public enum DocumentNodeType {

    /** 普通文档。 */
    DOCUMENT(1, "文档"),

    /** 可包含文档或子目录的目录。 */
    DIRECTORY(2, "目录");

    private final int code;
    private final String name;

    DocumentNodeType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按数据库编码解析；历史数据为空时按普通文档处理。
     *
     * @param code 数据库节点类型编码
     * @return 对应节点类型
     */
    public static DocumentNodeType fromCode(Integer code) {
        if (code == null) {
            return DOCUMENT;
        }
        for (DocumentNodeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return DOCUMENT;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
