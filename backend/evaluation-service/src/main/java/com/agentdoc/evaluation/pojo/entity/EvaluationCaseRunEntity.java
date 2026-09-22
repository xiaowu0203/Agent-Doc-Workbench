package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_case_run")
@Schema(description = "评估逻辑用例运行")
public class EvaluationCaseRunEntity extends BaseEntity {
    @Schema(description = "EvaluationRun ID") private Long runId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "测试用例版本 ID") private Long testCaseVersionId;
    @Schema(description = "状态") private String status;
    @Schema(description = "当前 Attempt ID") private Long currentAttemptId;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
