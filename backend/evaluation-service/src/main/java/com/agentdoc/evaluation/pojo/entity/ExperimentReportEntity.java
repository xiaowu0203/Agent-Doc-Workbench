package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("experiment_report")
@Schema(description = "不可变离线实验报告")
public class ExperimentReportEntity extends BaseEntity {
    @Schema(description = "Experiment ID") private Long experimentId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "Experiment 内单调递增修订号") private Integer revision;
    @Schema(description = "报告使用的 manifest hash") private String manifestHash;
    @Schema(description = "报告计算 schema 版本") private Integer calculationSchemaVersion;
    @Schema(description = "选中记录及计算输入 hash") private String calculationInputHash;
    @Schema(description = "选中的 Run、Attempt、Result、Metric、Feedback ID JSON")
    private String selectedRecordIdsJson;
    @Schema(description = "报告内容 schema 版本") private Integer reportSchemaVersion;
    @Schema(description = "逐用例与聚合报告 JSON") private String reportJson;
    @Schema(description = "报告内容 hash") private String contentHash;
    @Schema(description = "显式重算幂等键；自动首版为空") private String recalculationRequestKey;
    @Schema(description = "显式重算请求 hash") private String recalculationRequestHash;
    @Schema(description = "生成或重算主体") private Long generatedBy;
}
