package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.Getter;

/**
 * SkillVersion状态枚举
 */
@Getter
public enum SkillVersionStatus {
    /**
     * 草稿：刚上传ZIP包，尚未发布，不可被Agent使用，支持重新发布
     */
    DRAFT(0),
    /**
     * 已发布：正式可用状态，可被Agent绑定引用，发布后不允许修改
     */
    PUBLISHED(1);

    private final int code;

    SkillVersionStatus(int code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return value != null && code == value;
    }

    public static SkillVersionStatus fromCode(Integer code) {
        for (SkillVersionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill 版本状态非法");
    }
}
