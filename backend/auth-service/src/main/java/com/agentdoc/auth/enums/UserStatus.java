package com.agentdoc.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户账号状态。
 */
@Schema(description = "用户账号状态")
public enum UserStatus {

    /** 禁用 */
    DISABLED(0),

    /** 正常启用 */
    ENABLED(1);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 判断状态值是否为启用。
     * @param status 实体中的状态值
     * @return true 已启用；null 或非启用值返回 false
     */
    public static boolean isEnabled(Integer status) {
        return status != null && status == ENABLED.code;
    }
}
