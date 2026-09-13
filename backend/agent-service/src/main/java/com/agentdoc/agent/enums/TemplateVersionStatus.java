package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.Getter;

/** Agent、MCP 系统模板版本状态。 */
@Getter
public enum TemplateVersionStatus {
    /** 尚未发布、允许编辑。 */
    DRAFT(0),
    /** 已发布、允许安装。 */
    PUBLISHED(1),
    /** 已停用、仅保留历史读取能力。 */
    DISABLED(2);

    private final int code;

    TemplateVersionStatus(int code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return value != null && code == value;
    }

    public static TemplateVersionStatus fromCode(Integer code) {
        for (TemplateVersionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "模板版本状态非法");
    }
}
