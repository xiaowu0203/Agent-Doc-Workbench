package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建 EvaluationRun")
public record EvaluationRunCreateDTO(
        @NotNull @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "已发布 DatasetVersion ID；与单用例二选一") Long datasetVersionId,
        @Schema(description = "已发布 TestCaseVersion ID；与数据集二选一") Long singleTestCaseVersionId,
        @NotNull @Min(300) @Max(86400)
        @Schema(description = "WorkerCapability 有效期秒数") Long workerCapabilityTtlSeconds) {
}
