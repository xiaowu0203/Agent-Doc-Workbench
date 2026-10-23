package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 创建前的冻结来源配置核对，不替代创建/启动时的动态预检。 */
@Schema(description = "数据集离线实验来源预览")
public record DatasetExperimentSourceVO(
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "数据集版本 ID") Long datasetVersionId,
        @Schema(description = "启用用例数") int enabledCaseCount,
        @Schema(description = "冻结来源执行配置一致且代表来源可核验") boolean sourceConsistent,
        @Schema(description = "核对问题；为空仅表示本次来源核对通过") List<String> issues,
        @Schema(description = "代表来源任务 ID；核对失败时为空") Long sourceTaskId,
        @Schema(description = "代表来源执行 ID；核对失败时为空") Long sourceExecutionId,
        @Schema(description = "冻结 Agent ID；核对失败时为空") Long agentId,
        @Schema(description = "代表来源任务冻结预算，不代表整个实验授权") Long tokenBudget,
        @Schema(description = "共同执行快照 schema；核对失败时为空") Integer executionSnapshotSchemaVersion,
        @Schema(description = "共同执行快照 hash；核对失败时为空") String executionSnapshotHash) { }
