package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

/** Skill 定义的归属作用域。 */
public enum SkillScopeType {
    SYSTEM,
    SPACE;

    public boolean matches(String value) {
        return name().equals(value);
    }

    /** 历史单元测试和迁移前对象未赋值时按空间 Skill 处理。 */
    public static SkillScopeType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return SPACE;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 作用域非法");
        }
    }
}
