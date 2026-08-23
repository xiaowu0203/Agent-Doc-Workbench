package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 变更请求状态（审批流状态机）。
 * <p>流转：PENDING → APPROVED → MERGED；PENDING → REJECTED / RETURNED。</p>
 */
@Schema(description = "变更请求状态")
public enum ChangeRequestStatus {

    /** 待审批 */
    PENDING(0, "待审批"),

    /** 已通过（待合并） */
    APPROVED(1, "已通过"),

    /** 已拒绝 */
    REJECTED(2, "已拒绝"),

    /** 已合并 */
    MERGED(3, "已合并"),

    /** 已退回（带批注） */
    RETURNED(4, "已退回");

    private final int code;
    private final String name;

    ChangeRequestStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 按数据库编码解析，未知编码返回 null。
     * @param code 数据库 status 字段值
     * @return 对应枚举，未知返回 null
     */
    public static ChangeRequestStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ChangeRequestStatus status : values()) {
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
