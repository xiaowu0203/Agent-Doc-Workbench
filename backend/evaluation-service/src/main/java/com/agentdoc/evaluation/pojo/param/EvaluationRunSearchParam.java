package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "EvaluationRun 分页查询条件")
public class EvaluationRunSearchParam extends PageParam {

    @Schema(description = "所属空间 ID，必填")
    private Long spaceId;

    @Schema(description = "运行状态")
    private EvaluationRunStatus status;

    @Schema(description = "数据集版本 ID")
    private Long datasetVersionId;

    @Schema(description = "单测试用例版本 ID")
    private Long singleTestCaseVersionId;

    @Schema(description = "Experiment Variant ID")
    private Long experimentVariantId;

    @Schema(description = "创建时间起点，包含")
    private LocalDateTime createdFrom;

    @Schema(description = "创建时间终点，包含")
    private LocalDateTime createdTo;

    @Schema(description = "开始时间起点，包含")
    private LocalDateTime startedFrom;

    @Schema(description = "开始时间终点，包含")
    private LocalDateTime startedTo;

    @Override
    public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 必须为正整数");
        }
        requireRange(createdFrom, createdTo, "创建时间");
        requireRange(startedFrom, startedTo, "开始时间");
    }

    private static void requireRange(LocalDateTime from, LocalDateTime to, String field) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + "范围无效");
        }
    }
}
