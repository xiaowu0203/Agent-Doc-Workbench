package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评估运行DTO
 * <p>
 * 启动一轮评估运行，支持两种模式：数据集批量运行 / 单个用例运行。
 * datasetVersionId、singleTestCaseVersionId 二选一，不能同时为空或同时传入；
 * 引用的 DatasetVersion / TestCaseVersion 必须为已发布 LIVE 版本，草稿不可用于评估运行。
 *
 * @param spaceId                 归属空间ID
 * @param datasetVersionId        已发布数据集版本ID；与单用例参数二选一
 * @param singleTestCaseVersionId 单个已发布测试用例版本ID；与数据集参数二选一
 * @param workerCapabilityTtlSeconds WorkerCapability令牌有效期，单位秒，范围300～86400
 */
@Schema(description = "创建 EvaluationRun")
public record EvaluationRunCreateDTO(
        @NotNull(message = "spaceId 不能为空")
        @Schema(description = "所属空间 ID")
        Long spaceId,

        @Schema(description = "已发布 DatasetVersion ID；与单用例二选一")
        Long datasetVersionId,

        @Schema(description = "已发布 TestCaseVersion ID；与数据集二选一")
        Long singleTestCaseVersionId,

        @NotNull(message = "workerCapabilityTtlSeconds 不能为空")
        @Min(value = 300, message = "令牌有效期不能小于300秒")
        @Max(value = 86400, message = "令牌有效期不能大于86400秒")
        @Schema(description = "WorkerCapability 有效期秒数")
        Long workerCapabilityTtlSeconds
) {}