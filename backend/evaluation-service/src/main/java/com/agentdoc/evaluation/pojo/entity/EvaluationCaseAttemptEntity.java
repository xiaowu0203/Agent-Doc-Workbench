package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_case_attempt")
@Schema(description = "评估 Replay 尝试")
public class EvaluationCaseAttemptEntity extends BaseEntity {
    @Schema(description = "CaseRun ID") private Long caseRunId;
    @Schema(description = "EvaluationRun ID") private Long runId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "尝试序号") private Integer attemptNo;
    @Schema(description = "Replay Task ID") private Long replayTaskId;
    @Schema(description = "WorkerCapability Segment ID") private Long capabilitySegmentId;
    @Schema(description = "状态") private String status;
    @Schema(description = "失败阶段") private String failureStage;
    @Schema(description = "稳定失败码") private String failureCode;
    @Schema(description = "脱敏失败说明") private String failureMessage;
    @Schema(description = "开始时间") private LocalDateTime startedAt;
    @Schema(description = "结束时间") private LocalDateTime finishedAt;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
