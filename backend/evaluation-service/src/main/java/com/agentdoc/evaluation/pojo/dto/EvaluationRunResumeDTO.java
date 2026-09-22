package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * EvaluationRun 恢复时重新签发 WorkerCapability 的参数。
 * <p>
 * 恢复已暂停/中断的评估运行，仅指定新令牌有效期；评估运行本身的上下文不变，
 * 重新下发窄权限 WorkerCapability 供 Worker 继续执行剩余用例。
 *
 * @param workerCapabilityTtlSeconds 新签发 WorkerCapability 的有效期，单位秒
 */
@Schema(description = "恢复评估运行参数")
public record EvaluationRunResumeDTO(
        @NotNull(message = "workerCapabilityTtlSeconds 不能为空")
        @Schema(description = "WorkerCapability 有效期秒数")
        Long workerCapabilityTtlSeconds
) {}