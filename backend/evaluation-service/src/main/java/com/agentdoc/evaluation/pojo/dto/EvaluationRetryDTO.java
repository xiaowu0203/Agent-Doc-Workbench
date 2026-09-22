package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Evaluator 重试范围；空列表表示重试该 TestCaseVersion 绑定的全部 Evaluator。 */
public record EvaluationRetryDTO(
        @Schema(description = "需要重试的 EvaluatorVersion ID；为空表示全部")
        List<Long> evaluatorVersionIds,
        @Schema(description = "重新签发 WorkerCapability 的有效期秒数")
        @NotNull Long workerCapabilityTtlSeconds) {
}
