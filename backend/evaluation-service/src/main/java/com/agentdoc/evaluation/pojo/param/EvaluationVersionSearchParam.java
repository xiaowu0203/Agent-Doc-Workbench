package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "评估资源版本分页查询参数")
public class EvaluationVersionSearchParam extends PageParam {

    @Schema(description = "归属空间ID，必填")
    private Long spaceId;

    @Schema(description = "父资源ID（数据集/用例/评估器主记录ID），可选")
    private Long parentId;

    @Schema(description = "版本状态：DRAFT草稿 / LIVE已发布")
    private EvaluationVersionStatus status;

    @Override
    public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0 || (parentId != null && parentId <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "版本查询参数无效");
        }
    }
}