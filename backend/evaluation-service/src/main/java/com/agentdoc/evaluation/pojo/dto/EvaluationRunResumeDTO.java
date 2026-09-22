package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** EvaluationRun 恢复时重新签发 WorkerCapability 的参数。 */
public record EvaluationRunResumeDTO(
        @Schema(description = "WorkerCapability 有效期秒数") @NotNull Long workerCapabilityTtlSeconds) {
}
