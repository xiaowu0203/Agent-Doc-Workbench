package com.agentdoc.common.feign.vo;

/** 报告哈希专用冻结计价投影；小数与时间以规范文本进入schema2，不改变旧用量契约。
 * @param executionId 执行 @param modelId 模型 @param modelConfigVersion 模型版本
 * @param inputPricePerMillion 输入单价文本 @param outputPricePerMillion 输出单价文本 @param currency 币种
 * @param pricingSchemaVersion 计价schema @param pricingCapturedAt 冻结价格时间
 * @param inputTokens 输入量 @param inputTokensEstimated 是否估算 @param cachedInputTokens 缓存输入
 * @param cachedInputTokensEstimated 缓存是否估算 @param outputTokens 输出量 @param outputTokensEstimated 输出是否估算 */
public record OnlineReportUsageVO(Long executionId, Long modelId, Long modelConfigVersion, String inputPricePerMillion,
        String outputPricePerMillion, String currency, Integer pricingSchemaVersion, String pricingCapturedAt, Long inputTokens,
        Boolean inputTokensEstimated, Long cachedInputTokens, Boolean cachedInputTokensEstimated, Long outputTokens, Boolean outputTokensEstimated) {
    public static OnlineReportUsageVO from(AgentExecutionTokenUsageVO source) {
        if (source == null) { return null; }
        return new OnlineReportUsageVO(source.executionId(), source.modelId(), source.modelConfigVersion(),
                source.inputPricePerMillion() == null ? null : source.inputPricePerMillion().stripTrailingZeros().toPlainString(),
                source.outputPricePerMillion() == null ? null : source.outputPricePerMillion().stripTrailingZeros().toPlainString(), source.currency(),
                source.pricingSchemaVersion(), source.pricingCapturedAt() == null ? null : source.pricingCapturedAt().toString(), source.inputTokens(),
                source.inputTokensEstimated(), source.cachedInputTokens(), source.cachedInputTokensEstimated(), source.outputTokens(), source.outputTokensEstimated());
    }
}
