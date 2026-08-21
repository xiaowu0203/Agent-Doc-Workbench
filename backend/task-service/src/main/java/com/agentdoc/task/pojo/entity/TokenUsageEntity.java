package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Token 用量实体")
public class TokenUsageEntity extends BaseEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "Agent ID")
    private Long agentId;

    @Schema(description = "文档 ID")
    private Long documentId;

    @Schema(description = "统计维度：1 空间 / 2 文档 / 3 任务 / 4 Agent")
    private Integer dimension;

    @Schema(description = "Token 消耗量")
    private Long tokens;

    @Schema(description = "统计日期")
    private LocalDate usageDate;
}
