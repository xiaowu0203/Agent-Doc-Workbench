package com.agentdoc.task.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class TaskEntity extends BaseLogicDeleteEntity {

    private Long spaceId;

    private Long agentId;

    private Long documentId;

    private String instruction;

    /** 状态：0 待运行 / 1 运行中 / 2 已完成 / 3 已终止 / 4 异常 */
    private Integer status;

    private Long tokenBudget;

    private Long tokensUsed;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long createdBy;
}
