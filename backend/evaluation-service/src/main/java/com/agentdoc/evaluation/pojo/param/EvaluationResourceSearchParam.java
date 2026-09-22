package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EvaluationResourceSearchParam extends PageParam {
    private Long spaceId;
    private String keyword;
    private Boolean archived;

    @Override
    public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0 || keyword != null && keyword.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源查询参数无效");
        }
    }
}
