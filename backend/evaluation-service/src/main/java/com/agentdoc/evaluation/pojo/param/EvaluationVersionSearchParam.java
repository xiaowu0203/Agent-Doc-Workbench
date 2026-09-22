package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EvaluationVersionSearchParam extends PageParam {
    private Long spaceId;
    private Long parentId;
    private EvaluationVersionStatus status;

    @Override
    public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0 || parentId != null && parentId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "版本查询参数无效");
        }
    }
}
