package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "评估资源通用分页查询参数")
public class EvaluationResourceSearchParam extends PageParam {

    @Schema(description = "归属空间ID，必填")
    private Long spaceId;

    @Schema(description = "搜索关键词，可选，最大长度100")
    private String keyword;

    @Schema(description = "归档状态过滤：true仅查已归档，false仅查未归档，null不过滤")
    private Boolean archived;

    @Override
    public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0 || (keyword != null && keyword.length() > 100)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源查询参数无效");
        }
    }
}