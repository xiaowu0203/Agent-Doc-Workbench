package com.agentdoc.task.entity;

import com.agentdoc.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * Token 用量实体（四维度统计）。
 * 注意：本表为流水统计表，无 deleted / updated_at 列，继承 {@link BaseEntity}（id/createdAt）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("token_usage")
public class TokenUsageEntity extends BaseEntity {

    private Long spaceId;

    private Long taskId;

    private Long agentId;

    private Long documentId;

    /** 维度：1 空间 / 2 文档 / 3 任务 / 4 Agent */
    private Integer dimension;

    private Long tokens;

    private LocalDate usageDate;
}
