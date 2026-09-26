package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("experiment")
@Schema(description = "离线实验")
public class ExperimentEntity extends BaseEntity {
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "冻结的数据集版本 ID") private Long datasetVersionId;
    @Schema(description = "Experiment 状态") private String status;
    @Schema(description = "客户端创建幂等键") private String clientRequestKey;
    @Schema(description = "创建请求 hash") private String requestHash;
    @Schema(description = "manifest schema 版本") private Integer manifestSchemaVersion;
    @Schema(description = "冻结 manifest JSON") private String manifestJson;
    @Schema(description = "manifest hash") private String manifestHash;
    @Schema(description = "启动时确认的计划 Token 授权上限") private Long authorizedTokenBudget;
    @Schema(description = "稳定失败码") private String failureCode;
    @Schema(description = "脱敏失败说明") private String failureMessage;
    @Schema(description = "创建人") private Long createdBy;
    @Schema(description = "启动人") private Long startedBy;
    @Schema(description = "启动时间") private LocalDateTime startedAt;
    @Schema(description = "取消请求人") private Long cancelRequestedBy;
    @Schema(description = "取消请求时间") private LocalDateTime cancelRequestedAt;
    @Schema(description = "人工结论") private String decision;
    @Schema(description = "人工结论理由") private String decisionReason;
    @Schema(description = "人工结论引用的报告 revision") private Integer decisionReportRevision;
    @Schema(description = "决策人") private Long decidedBy;
    @Schema(description = "决策时间") private LocalDateTime decidedAt;
    @Schema(description = "结束时间") private LocalDateTime finishedAt;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
