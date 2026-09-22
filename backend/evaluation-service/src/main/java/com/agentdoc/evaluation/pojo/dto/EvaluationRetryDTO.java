package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Evaluator 重试范围DTO
 * <p>
 * 指定本次用例尝试需要重试哪些评估器；evaluatorVersionIds 传空列表代表重试该 TestCaseVersion 绑定的全部 Evaluator。
 * 同时指定新下发 WorkerCapability JWT 的过期时长。
 *
 * @param evaluatorVersionIds    需要重试的评估器版本ID集合；空列表=全部绑定评估器都重试
 * @param workerCapabilityTtlSeconds 新签发 WorkerCapability 的有效期，单位秒
 */
@Schema(description = "评估器重试参数")
public record EvaluationRetryDTO(
        @Schema(description = "需要重试的 EvaluatorVersion ID；为空表示全部")
        List<Long> evaluatorVersionIds,

        @NotNull(message = "workerCapabilityTtlSeconds 不能为空")
        @Schema(description = "重新签发 WorkerCapability 的有效期秒数")
        Long workerCapabilityTtlSeconds
) {}