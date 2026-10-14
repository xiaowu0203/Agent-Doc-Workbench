package com.agentdoc.evaluation.pojo.entity;
import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.math.BigInteger;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_assignment")
@Schema(description = "不可变线上分配身份及独立状态投影")
public class OnlineAssignmentEntity extends BaseEntity {
    @Schema(description = "Agent权威执行状态") private String executionStatus;
    @Schema(description = "Agent实际终态观察时间") private LocalDateTime executionTerminalAt;
    @Schema(description = "最近权威观察时间") private LocalDateTime lastObservedAt;
    @Schema(description = "本轮未知事实首次发现时间") private LocalDateTime unresolvedSince;
    @Schema(description = "实验") private Long experimentId;
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "Agent") private Long agentId;
    @Schema(description = "预分配Task") private Long taskId;
    @Schema(description = "文档") private Long documentId;
    @Schema(description = "发起者") private Long createdBy;
    @Schema(description = "请求键") private String clientRequestKey;
    @Schema(description = "请求摘要") private String requestHash;
    @Schema(description = "输入协议") private Integer inputSnapshotSchemaVersion;
    @Schema(description = "输入摘要") private String inputSnapshotHash;
    @Schema(description = "稳定桶") private Integer bucket;
    @Schema(description = "稳定组") private String variant;
    @Schema(description = "旧候选身份，schema 2 不使用") private Long candidateConfigId;
    @Schema(description = "新模板身份") private Long templateId;
    @Schema(description = "模板协议") private Integer configSchemaVersion;
    @Schema(description = "模板摘要") private String configHash;
    @Schema(description = "依赖摘要") private String dependencyHash;
    @Schema(description = "绑定协议") private Integer bindingSchemaVersion;
    @Schema(description = "绑定摘要") private String bindingHash;
    @Schema(description = "私有绑定") private String bindingJson;
    @Schema(description = "接受顺序") private Long acceptedSequence;
    @Schema(description = "Token预留") private Long reservedTokenBudget;
    @Schema(description = "确认状态") private String taskConfirmationStatus;
    @Schema(description = "派发状态") private String dispatchStatus;
    @Schema(description = "占槽状态") private String slotStatus;
    @Schema(description = "取消状态") private String cancelStatus;
    @Schema(description = "结算状态") private String settlementStatus;
    @Schema(description = "权威执行身份") private Long executionId;
    @Schema(description = "实际Token未知为空，真实超额不截断") private BigInteger consumedTokens;
    @Schema(description = "未决原因") private String reasonCode;
    @Schema(description = "确认时间") private LocalDateTime taskConfirmedAt;
    @Schema(description = "结算时间") private LocalDateTime settledAt;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
