package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Token 消耗原始调用明细实体【真相源，每次 MCP 调用无条件落一条】。
 * 流水表：无 deleted / updated_at，继承 {@link BaseEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("token_usage_detail")
@Schema(description = "Token 消耗明细实体（真相源）")
public class TokenUsageDetailEntity extends BaseEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "关联任务 ID")
    private Long taskId;

    @Schema(description = "关联 Agent ID")
    private Long agentId;

    @Schema(description = "关联模型 ID")
    private Long modelId;

    @Schema(description = "输入总 token")
    private Long inputTokens;

    @Schema(description = "缓存命中输入 token，MCP 不支持则为 NULL")
    private Long cachedInputTokens;

    @Schema(description = "输出 token")
    private Long outputTokens;

    @Schema(description = "MCP 调用发生时间")
    private LocalDateTime callTime;

    @Schema(description = "预估人民币费用，仅展示，可重新核算")
    private BigDecimal estimatedCost;

    @Schema(description = "链路 traceId，便于排查")
    private String traceId;
}
