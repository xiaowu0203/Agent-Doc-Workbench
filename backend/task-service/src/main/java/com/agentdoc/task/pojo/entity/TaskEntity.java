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

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "Agent ID")
    private Long agentId;

    @Schema(description = "目标文档 ID")
    private Long documentId;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "任务指令")
    private String instruction;

    @Schema(description = "状态：0 待运行 / 1 运行中 / 2 已完成 / 3 已终止 / 4 异常")
    private Integer status;

    @Schema(description = "Token 预算上限")
    private Long tokenBudget;

    @Schema(description = "已消耗 Token 数")
    private Long tokensUsed;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "最近一次失败原因")
    private String errorMessage;

    @Schema(description = "任务结果摘要")
    private String resultSummary;

    @Schema(description = "预留父任务 ID，Phase 3 不参与业务逻辑")
    private Long parentTaskId;

    @Schema(description = "消息重试次数")
    private Integer retryCount;

    @Schema(description = "加密保存的任务能力令牌")
    private String capabilityToken;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;
}
