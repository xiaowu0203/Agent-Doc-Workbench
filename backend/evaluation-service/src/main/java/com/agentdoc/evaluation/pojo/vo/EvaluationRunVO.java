package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "评估运行")
public record EvaluationRunVO(
        @Schema(description = "评估运行ID")
        Long id,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "数据集版本ID，批量模式使用；与单用例ID互斥")
        Long datasetVersionId,

        @Schema(description = "单个测试用例版本ID，单用例模式使用；与数据集版本ID互斥")
        Long singleTestCaseVersionId,

        @Schema(description = "评估运行状态")
        String status,

        @Schema(description = "暂停原因，仅暂停状态时有值")
        String pauseReason,

        @Schema(description = "是否已请求取消")
        Boolean cancelRequested,

        @Schema(description = "总用例数量")
        Integer caseCount,

        @Schema(description = "对账失败计数")
        Integer reconciliationFailureCount,

        @Schema(description = "运行开始时间")
        LocalDateTime startedAt,

        @Schema(description = "运行结束时间")
        LocalDateTime finishedAt,

        @Schema(description = "下属用例运行列表")
        List<EvaluationCaseRunVO> cases
) {
    /**
     * 实体转VO，携带用例运行数据
     * @param run 评估运行实体
     * @param cases 下属用例运行VO列表
     * @return 视图对象（用例列表做不可变拷贝）
     */
    public static EvaluationRunVO from(EvaluationRunEntity run, List<EvaluationCaseRunVO> cases) {
        return new EvaluationRunVO(run.getId(), run.getSpaceId(), run.getDatasetVersionId(),
                run.getSingleTestCaseVersionId(), run.getStatus(), run.getPauseReason(),
                run.getCancelRequested(), run.getCaseCount(), run.getReconciliationFailureCount(),
                run.getStartedAt(), run.getFinishedAt(),
                List.copyOf(cases));
    }
}