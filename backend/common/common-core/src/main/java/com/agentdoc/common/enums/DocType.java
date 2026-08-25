package com.agentdoc.common.enums;

/**
 * 文档类型（草稿 / 正式双文档模式）。
 */
public enum DocType {

    /** 正式文档：Agent 修改必须审批合并。 */
    FORMAL(1, "正式"),

    /** 草稿文档：Agent 可直接编辑。 */
    DRAFT(2, "草稿");

    /** 数据库存储及跨服务传输使用的类型编码。 */
    private final int code;

    /** 文档类型展示名称。 */
    private final String name;

    DocType(int code, String name) {
        this.code = code;
        this.name = name;
    }

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
