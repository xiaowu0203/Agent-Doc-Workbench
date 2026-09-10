package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.utils.JsonUtils;

import java.util.Map;

/**
 * 模型采样参数记录
 * 用于封装大模型调用时的采样控制参数
 *
 * @param temperature 温度，控制生成随机性，取值范围 0~2；无有效配置时为 null
 * @param topP Top‑P 采样率，核采样，取值范围 0~1；无有效配置时为 null
 */
public record ModelSamplingOptions(Double temperature, Double topP) {

    /**
     * 从模型实体的扩展配置JSON快照解析运行时采样参数
     * 只解析 temperature、topP 两个字段，其余自定义字段不处理，保留在原始JSON中
     *
     * @param model 模型实体，包含 optionsJson 扩展配置
     * @return 解析后的采样参数对象；无配置/解析失败返回 null 值的实例
     */
    public static ModelSamplingOptions from(ModelEntity model) {
        // 模型为空或扩展配置JSON为空，返回空采样配置
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

    /**
     * 数值范围校验工具方法
     * 校验对象是否为数字，并且落在 [minimum, maximum] 区间内
     * 非数字、非有限浮点数、超出范围均返回 null
     *
     * @param value 待校验原始对象
     * @param minimum 最小值（包含）
     * @param maximum 最大值（包含）
     * @return 合法返回对应Double，不合法返回null
     */
    private static Double numberInRange(Object value, double minimum, double maximum) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double parsed = number.doubleValue();
        return Double.isFinite(parsed) && parsed >= minimum && parsed <= maximum ? parsed : null;
    }
}
