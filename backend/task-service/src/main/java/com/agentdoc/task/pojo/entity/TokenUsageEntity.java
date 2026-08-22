package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * Token 用量聚合统计实体（仅保留历史完整自然日，用于折线趋势图）。
 * 注意：本表为流水统计表，无 deleted / updated_at 列，继承 {@link BaseEntity}（id/createdAt）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("token_usage")
@Schema(description = "Token 用量聚合统计实体")
public class TokenUsageEntity extends BaseEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "统计维度：1 空间 / 2 文档 / 3 任务 / 4 Agent")
    private Integer dimension;

    @Schema(description = "统计对象 ID，配合 dimension：1空间=space_id｜2文档=document_id｜3任务=task_id｜4Agent=agent_id")
    private Long objId;

    @Schema(description = "Token 当日增量数量")
    private Long tokens;

    @Schema(description = "统计日期")
    private LocalDate usageDate;
}
