package com.agentdoc.auth.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "部门状态")
public enum DepartmentStatus {

    /** 禁用。 */
    DISABLED(0),

    /** 启用。 */
    ENABLED(1);

    private final int code;

    DepartmentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DepartmentStatus fromCode(Integer code) {
        for (DepartmentStatus status : values()) {
            if (Integer.valueOf(status.code).equals(code)) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "部门状态无效");
    }
}
