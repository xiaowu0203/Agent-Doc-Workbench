package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.utils.JsonUtils;

import java.util.Map;

/**
 * 模型采样参数。
 *
 * @param temperature 温度，范围 0~2；无有效配置时为 null
 * @param topP Top-P 采样率，范围 0~1；无有效配置时为 null
 */
public record ModelSamplingOptions(Double temperature, Double topP) {

    /**
     * 从模型扩展配置快照解析当前运行时支持的采样参数。
     * 未识别字段仍保留在原始 JSON 中，不影响解析。
     */
    public static ModelSamplingOptions from(ModelEntity model) {
        if (model == null || model.getOptionsJson() == null || model.getOptionsJson().isBlank()) {
            return new ModelSamplingOptions(null, null);
        }
        Map<?, ?> options = JsonUtils.parse(model.getOptionsJson(), Map.class);
        if (options == null) {
            return new ModelSamplingOptions(null, null);
        }
        return new ModelSamplingOptions(
                numberInRange(options.get("temperature"), 0D, 2D),
                numberInRange(options.get("topP"), 0D, 1D));
    }

    private static Double numberInRange(Object value, double minimum, double maximum) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double parsed = number.doubleValue();
        return Double.isFinite(parsed) && parsed >= minimum && parsed <= maximum ? parsed : null;
    }
}
