package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_run")
@Schema(description = "评估运行")
public class EvaluationRunEntity extends BaseEntity {
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "数据集版本 ID") private Long datasetVersionId;
    @Schema(description = "单测试用例版本 ID") private Long singleTestCaseVersionId;
    @Schema(description = "Experiment Variant ID；普通 Run 为空") private Long experimentVariantId;
    @Schema(description = "运行状态") private String status;
    @Schema(description = "暂停原因") private String pauseReason;
    @Schema(description = "是否请求取消") private Boolean cancelRequested;
    @Schema(description = "用例数量") private Integer caseCount;
    @Schema(description = "连续对账基础设施失败次数") private Integer reconciliationFailureCount;
    @Schema(description = "创建人") private Long createdBy;
    @Schema(description = "开始时间") private LocalDateTime startedAt;
    @Schema(description = "结束时间") private LocalDateTime finishedAt;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
