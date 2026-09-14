package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单次 AgentExecution 的 Token 聚合用量实体【业务真相源】。
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

    @Schema(description = "AgentExecution ID，权威幂等键")
    private Long executionId;

    @Schema(description = "关联 Agent ID")
    private Long agentId;

    @Schema(description = "关联模型 ID")
    private Long modelId;

    @Schema(description = "执行时模型配置版本")
    private Long modelConfigVersion;

    @Schema(description = "冻结输入单价，元/百万 Token")
    private BigDecimal inputPricePerMillion;

    @Schema(description = "冻结输出单价，元/百万 Token")
    private BigDecimal outputPricePerMillion;

    @Schema(description = "计价币种")
    private String currency;

    @Schema(description = "计价公式 schema 版本")
    private Integer pricingSchemaVersion;

    @Schema(description = "价格快照捕获时间")
    private LocalDateTime pricingCapturedAt;

    @Schema(description = "输入总 token")
    private Long inputTokens;

    @Schema(description = "输入 Token 是否为本地估算值")
    private Boolean inputTokensEstimated;

    @Schema(description = "缓存命中输入 token，MCP 不支持则为 NULL")
    private Long cachedInputTokens;

    @Schema(description = "缓存输入 Token 是否为本地估算值")
    private Boolean cachedInputTokensEstimated;

    @Schema(description = "输出 token")
    private Long outputTokens;

    @Schema(description = "输出 Token 是否为本地估算值")
    private Boolean outputTokensEstimated;

    @Schema(description = "执行用量记账时间")
    private LocalDateTime callTime;

    @Schema(description = "预估人民币费用，仅展示，可重新核算")
    private BigDecimal estimatedCost;

    @Schema(description = "链路 traceId，便于排查")
    private String traceId;
}
