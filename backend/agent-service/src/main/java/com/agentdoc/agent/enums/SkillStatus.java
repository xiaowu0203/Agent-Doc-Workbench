package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.Getter;

/**
 * Skill状态枚举
 */
@Getter
public enum SkillStatus {
    /**
     * 禁用：Skill不可使用，不允许新建版本上传、Agent不可绑定
     */
    DISABLED(0),
    /**
     * 启用：正常可用，可上传新版本、允许Agent绑定使用
     */
    ACTIVE(1);

    private final int code;

    SkillStatus(int code) {
        this.code = code;
    }

    public static SkillStatus fromCode(Integer code) {
        for (SkillStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill 状态非法");
    }

    public boolean matches(Integer value) {
        return value != null && code == value;
    }
}
