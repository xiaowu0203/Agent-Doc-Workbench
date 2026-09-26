package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("experiment_variant")
@Schema(description = "离线实验不可变变体")
public class ExperimentVariantEntity extends BaseEntity {
    @Schema(description = "Experiment ID") private Long experimentId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "Experiment 内稳定 Variant key") private String variantKey;
    @Schema(description = "BASELINE 或 CANDIDATE") private String role;
    @Schema(description = "变体类型；首版仅 PROMPT") private String variantType;
    @Schema(description = "Agent 候选配置 ID；baseline 为空") private Long candidateConfigId;
    @Schema(description = "来源执行快照 schema 版本") private Integer sourceSnapshotSchemaVersion;
    @Schema(description = "来源执行快照 hash") private String sourceSnapshotHash;
    @Schema(description = "实际候选执行快照 schema 版本") private Integer candidateSnapshotSchemaVersion;
    @Schema(description = "实际候选执行快照 hash") private String candidateSnapshotHash;
    @Schema(description = "Prompt 差异字段路径 JSON") private String promptDiffFieldPaths;
    @Schema(description = "移除 systemPrompt 后的公共快照 hash") private String snapshotWithoutPromptHash;
    @Schema(description = "关联 EvaluationRun ID") private Long evaluationRunId;
}
