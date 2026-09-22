package com.agentdoc.evaluation.metric;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * 标准 Metric 写入前的唯一契约校验入口。
 * <p>
 * 静态工具类，负责校验指标值与指标目录定义的一致性；
 * 校验指标key、值类型、方向、来源、单位、数值约束，保证入库指标完全符合目录契约。
 * </p>
 */
public final class MetricContractValidator {
    /** 数值指标最大精度，对应数据库 DECIMAL(30,10) */
    private static final int MAX_NUMERIC_PRECISION = 30;
    /** 数值指标最大小数位数，对应数据库 DECIMAL(30,10) */
    private static final int MAX_NUMERIC_SCALE = 10;
    /** 字符串指标最大长度，与数据库字段长度对齐 */
    private static final int MAX_STRING_LENGTH = 191;

    // 私有构造，禁止实例化
    private MetricContractValidator() {
    }

    /**
     * 执行完整指标契约校验，校验通过返回指标目录定义
     *
     * @param evaluatorKey 当前产出指标的评估器key
     * @param value 待校验标准化指标值
     * @return 指标目录定义实体
     */
    public static MetricDefinition validate(String evaluatorKey, StandardMetricValue value) {
        if (value == null) {
            throw invalid("Metric 不能为空");
        }
        // 从指标目录加载该metricKey对应的定义，不存在直接抛异常
        MetricDefinition definition = MetricDefinitionCatalog.require(value.metricKey());
        // 校验值类型、优化方向、指标来源必须和目录定义保持一致
        if (definition.valueType() != value.valueType()
                || definition.direction() != value.direction()
                || definition.source() != value.source()) {
            throw invalid("Metric 类型、方向或来源与固定目录不一致");
        }
        // 目录绑定了评估器：只能由指定evaluatorKey产生该指标
        if (definition.evaluatorKey() != null && !definition.evaluatorKey().equals(evaluatorKey)) {
            throw invalid("Evaluator 不允许产生该 Metric");
        }
        // 执行型指标：目录不绑定评估器，则不能携带evaluatorKey
        if (definition.evaluatorKey() == null && evaluatorKey != null) {
            throw invalid("执行型 Metric 不能声明 Evaluator 生产者");
        }
        // 校验单位规则
        validateUnit(definition, value.unit());
        // 校验指标值本体，区分 NUMBER / BOOLEAN / STRING
        validateTypedValue(value);
        return definition;
    }

    /**
     * 校验指标单位：普通指标严格匹配目录；成本币种校验ISO-4217代码
     * @param definition 指标目录定义
     * @param unit 传入单位字符串
     */
    private static void validateUnit(MetricDefinition definition, String unit) {
        if (unit == null || unit.isBlank()) {
            throw invalid("Metric unit 不能为空");
        }
        // 非币种类指标：单位必须和目录完全一致
        if (!definition.currencyUnit()) {
            if (!definition.unit().equals(unit)) {
                throw invalid("Metric unit 与固定目录不一致");
            }
            return;
        }
        // 币种成本指标：校验ISO-4217大写币种编码
        try {
            if (!Currency.getInstance(unit).getCurrencyCode().equals(unit)) {
                throw invalid("成本 Metric 必须使用大写 ISO-4217 币种代码");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid("成本 Metric 必须使用有效 ISO-4217 币种代码");
        }
    }

    /**
     * 校验指标值本体：有且仅有一个类型字段赋值，类型与valueType匹配
     * @param value 标准化指标值
     */
    private static void validateTypedValue(StandardMetricValue value) {
        // 统计已赋值的类型字段数量，只能恰好1个
        int present = (value.numericValue() == null ? 0 : 1)
                + (value.booleanValue() == null ? 0 : 1)
                + (value.stringValue() == null ? 0 : 1);
        if (present != 1) {
            throw invalid("Metric 必须且只能设置一个 typed value");
        }
        // 按valueType校验对应字段
        if (value.valueType() == EvaluationMetricValueType.NUMBER) {
            validateNumber(value.numericValue(), value.unit());
        } else if (value.valueType() == EvaluationMetricValueType.BOOLEAN && value.booleanValue() == null) {
            throw invalid("BOOLEAN Metric 必须设置 booleanValue");
        } else if (value.valueType() == EvaluationMetricValueType.STRING) {
            if (value.stringValue() == null || value.stringValue().isBlank()
                    || value.stringValue().length() > MAX_STRING_LENGTH) {
                throw invalid("STRING Metric 必须是受控的非空短值");
            }
        }
        // 防御校验：禁止valueType以外的类型字段被赋值
        if (value.valueType() != EvaluationMetricValueType.NUMBER && value.numericValue() != null
                || value.valueType() != EvaluationMetricValueType.BOOLEAN && value.booleanValue() != null
                || value.valueType() != EvaluationMetricValueType.STRING && value.stringValue() != null) {
            throw invalid("typed value 与 valueType 不匹配");
        }
    }

    /**
     * 数值指标专项校验：精度范围、非负、不同unit额外业务约束
     * @param number 数值
     * @param unit 单位
     */
    private static void validateNumber(BigDecimal number, String unit) {
        if (number == null || number.precision() > MAX_NUMERIC_PRECISION
                || Math.max(number.scale(), 0) > MAX_NUMERIC_SCALE) {
            throw invalid("NUMBER Metric 超出 DECIMAL(30,10) 范围");
        }
        // 首版不支持负数指标
        if (number.signum() < 0) {
            throw invalid("首版标准 Metric 不接受负数");
        }
        // token/count/millisecond 要求必须为整数，不允许小数
        if (("token".equals(unit) || "count".equals(unit) || "millisecond".equals(unit))
                && number.stripTrailingZeros().scale() > 0) {
            throw invalid("token、count 和 millisecond Metric 必须是整数");
        }
        // ratio / score 取值区间 [0,1]
        if (("ratio".equals(unit) || "score".equals(unit)) && number.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("ratio 和 score Metric 必须位于 0 到 1");
        }
    }

    /**
     * 构造参数非法业务异常
     * @param message 错误信息
     * @return BusinessException
     */
    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}

