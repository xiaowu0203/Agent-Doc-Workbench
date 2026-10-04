package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_experiment")
@Schema(description = "OnlineExperimentEntity")
public class OnlineExperimentEntity extends BaseEntity {
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "Agent") private Long agentId;
    @Schema(description = "实验名称") private String name;
    @Schema(description = "幂等键") private String clientRequestKey;
    @Schema(description = "创建请求摘要") private String requestHash;
    @Schema(description = "线上协议版本") private Integer manifestSchemaVersion;
    @Schema(description = "私有冻结清单") private String manifestJson;
    @Schema(description = "清单摘要") private String manifestHash;
    @Schema(description = "运行状态") private String status;
    @Schema(description = "空间运行占位") private Integer activeSlot;
    @Schema(description = "状态版本") private Long stateVersion;
    @Schema(description = "接受序号") private Long acceptedSequence;
    @Schema(description = "授权Token") private Long authorizedTokenBudget;
    @Schema(description = "任务上限") private Integer maxTaskCount;
    @Schema(description = "已分配数") private Integer assignedTaskCount;
    @Schema(description = "未结算预留") private Long reservedTokenBudget;
    @Schema(description = "实际账本Token") private Long consumedTokens;
    @Schema(description = "拒绝/暂停原因") private String reasonCode;
    @Schema(description = "创建者") private Long createdBy;
    @Schema(description = "状态操作者") private Long stateChangedBy;
    @Schema(description = "状态时间") private LocalDateTime stateChangedAt;
    @Schema(description = "启动时间") private LocalDateTime startedAt;
    @Schema(description = "分配截止") private LocalDateTime assignmentDeadline;
    @Schema(description = "观察截止") private LocalDateTime observationDeadline;
    @Schema(description = "最近对账") private LocalDateTime lastReconciledAt;
    @Schema(description = "紧急停止请求时间") private LocalDateTime emergencyStopRequestedAt;
    @Schema(description = "基线占槽数") private Integer baselineSlotCount;
    @Schema(description = "候选占槽数") private Integer candidateSlotCount;
    @Schema(description = "未知任务数") private Integer unknownTaskCount;
    @Schema(description = "停止请求时间") private LocalDateTime stopRequestedAt;
    @Schema(description = "停止时间") private LocalDateTime stoppedAt;
    @Schema(description = "人工决定") private String decision;
    @Schema(description = "决定依据") private String decisionReason;
    @Schema(description = "决定绑定版本") private Integer decisionReportRevision;
    @Schema(description = "决定人") private Long decidedBy;
    @Schema(description = "决定时间") private LocalDateTime decidedAt;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
