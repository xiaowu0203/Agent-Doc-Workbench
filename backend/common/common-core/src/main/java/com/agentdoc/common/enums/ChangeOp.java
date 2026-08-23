package com.agentdoc.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 变更操作类型
 * <ul>
 * <li>{@link #REPLACE}：全文替换（newText 为新全文，oldText 可作校验）</li>
 * <li>{@link #APPEND}：末尾追加（newText 为追加内容）</li>
 * </ul>
 */
public enum ChangeOp {

    /** 全文替换 */
    REPLACE("replace"),

    /** 末尾追加 */
    APPEND("append");

    private final String code;

    ChangeOp(String code) {
        this.code = code;
    }

    /**
     * 按字符串编码解析，未知编码返回 null。
     *
     * @param code 操作编码
     * @return 对应枚举，未知返回 null
     */
    public static ChangeOp fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ChangeOp op : values()) {
            if (op.code.equals(code)) {
                return op;
            }
        }
        return null;
    }

    /**
     * JSON 序列化值（小写操作名），反序列化按此匹配。
     *
     * @return 操作编码
     */
    @JsonValue
    public String getCode() {
        return code;
    }
}
