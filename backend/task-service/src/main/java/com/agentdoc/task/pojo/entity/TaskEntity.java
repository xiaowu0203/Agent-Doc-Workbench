package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
@Schema(description = "任务实体")
public class TaskEntity extends BaseLogicDeleteEntity {

    @Schema(description = "可读任务编号")
    private String taskNo;

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "Agent ID")
    private Long agentId;

    @Schema(description = "Agent 配置版本")
    private Long agentConfigVersion;

    @Schema(description = "Agent 执行 ID")
    private Long agentExecutionId;

    @Schema(description = "任务创建或首次派发时的 OpenTelemetry Trace ID")
    private String traceId;

    @Schema(description = "A2A 任务 ID")
    private String a2aTaskId;

    @Schema(description = "A2A 任务上下文 ID")
    private String a2aContextId;

    @Schema(description = "prompt 哈希值")
    private String promptHash;

    @Schema(description = "目标文档 ID")
    private Long documentId;

    @Schema(description = "创建任务时的文档类型快照")
    private Integer documentType;

    @Schema(description = "创建任务时冻结的正式文档版本")
    private Long documentVersionSnapshot;

    @Schema(description = "冻结文档正文 SHA-256")
    private String documentContentSha256;

    @Schema(description = "任务输入快照 schema 版本")
    private Integer inputSnapshotSchemaVersion;

    @Schema(description = "任务输入快照稳定 SHA-256")
    private String inputSnapshotHash;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "任务指令")
    private String instruction;

    @Schema(description = "状态：0 待运行 / 1 运行中 / 2 已完成 / 3 已终止 / 4 异常")
    private Integer status;

    @Schema(description = "Token 预算上限")
    private Long tokenBudget;

    @Schema(description = "文档读取范围：FULL / RANGES")
    private String readScope;

    @Schema(description = "关注区域 JSON；RANGES 时同时作为读取白名单")
    private String focusRegionsJson;

    @Schema(description = "已消耗 Token 数")
    private Long tokensUsed;

    @Schema(description = "已消耗 Token 是否包含本地估算值")
    private Boolean tokensEstimated;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "派发时间")
    private LocalDateTime dispatchedAt;

    @Schema(description = "最近一次心跳时间")
    private LocalDateTime lastHeartbeatAt;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "最近一次失败原因")
    private String errorMessage;

    @Schema(description = "任务结果摘要")
    private String resultSummary;

    @Schema(description = "直接来源任务 ID；当前用于重跑和审批重改血缘")
    private Long parentTaskId;

    @Schema(description = "逻辑工作根任务 ID；根任务指向自身")
    private Long rootTaskId;

    @Schema(description = "执行血缘类型")
    private String lineageType;

    @Schema(description = "执行副作用模式")
    private String executionMode;

    @Schema(description = "消息重试次数")
    private Integer retryCount;

    @Schema(description = "加密保存的任务能力令牌")
    private String capabilityToken;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;
}
