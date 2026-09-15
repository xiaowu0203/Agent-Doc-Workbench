package com.agentdoc.task.convertor;

import com.agentdoc.task.enums.TokenSnapshotType;
import com.agentdoc.task.enums.TokenUsageDimension;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenDailySnapshotEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.agentdoc.task.pojo.entity.TokenUsageEntity;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.pojo.vo.TokenUsageAggregateRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Token 用量明细、快照和聚合实体转换器。
 */
public final class TokenUsageConvertor {

    private TokenUsageConvertor() {
    }

    public static TokenUsageDetailEntity toDetail(TaskEntity task, A2aTokenUsage usage,
                                                   BigDecimal estimatedCost, String traceId) {
        TokenUsageDetailEntity entity = new TokenUsageDetailEntity();
        entity.setSpaceId(task.getSpaceId());
        entity.setTaskId(task.getId());
        entity.setExecutionId(usage.executionId());
        entity.setAgentId(task.getAgentId());
        entity.setModelId(usage.modelId());
        entity.setModelConfigVersion(usage.modelConfigVersion());
        entity.setInputPricePerMillion(usage.inputPricePerMillion());
        entity.setOutputPricePerMillion(usage.outputPricePerMillion());
        entity.setCurrency(usage.currency());
        entity.setPricingSchemaVersion(usage.pricingSchemaVersion());
        entity.setPricingCapturedAt(usage.pricingCapturedAt());
        entity.setInputTokens(usage.inputTokens());
        entity.setInputTokensEstimated(usage.inputTokensEstimated());
        entity.setCachedInputTokens(usage.cachedInputTokens());
        entity.setCachedInputTokensEstimated(usage.cachedInputTokensEstimated());
        entity.setOutputTokens(usage.outputTokens());
        entity.setOutputTokensEstimated(usage.outputTokensEstimated());
        entity.setCallTime(LocalDateTime.now());
        entity.setEstimatedCost(estimatedCost);
        entity.setTraceId(traceId);
        return entity;
    }

    public static TokenDailySnapshotEntity toSnapshot(Long spaceId, LocalDate date, Long input, Long output,
                                                       BigDecimal cost, TokenSnapshotType type) {
        TokenDailySnapshotEntity entity = new TokenDailySnapshotEntity();
        entity.setSpaceId(spaceId);
        entity.setSnapshotDate(date);
        entity.setTotalInput(input == null ? 0 : input);
        entity.setTotalOutput(output == null ? 0 : output);
        entity.setTotalEstimatedCost(cost);
        entity.setSnapshotType(type.getCode());
        return entity;
    }

    public static TokenUsageEntity toAggregate(TokenUsageAggregateRow row, LocalDate date,
                                                TokenUsageDimension dimension) {
        TokenUsageEntity entity = new TokenUsageEntity();
        entity.setSpaceId(row.spaceId());
        entity.setDimension(dimension.getCode());
        entity.setObjId(row.objId());
        entity.setTokens(row.tokens());
        entity.setUsageDate(date);
        return entity;
    }
}
