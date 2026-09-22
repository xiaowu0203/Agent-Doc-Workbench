package com.agentdoc.evaluation.metric;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import static com.agentdoc.evaluation.constant.EvaluationConstant.METRIC_CONTRACT_VERSION;

/**
 * 在进入 Mapper 前统一生成已经通过目录与归属校验的 Metric 实体。
 * <p>
 * 静态工厂类，负责组装 EvaluationMetricEntity；
 * 前置完成上下文完整性、指标契约、生产者身份校验，保证入库实体合法。
 * </p>
 */
public final class EvaluationMetricFactory {
    // 私有构造，禁止实例化
    private EvaluationMetricFactory() {
    }

    /**
     * 创建评估指标数据库实体，前置执行全套校验
     *
     * @param context 指标写入上下文，承载空间、任务、用例、生产者等归属信息
     * @param value   上层传入的标准化指标值对象
     * @return 已填充字段、校验通过的 EvaluationMetricEntity，可直接送入Mapper
     */
    public static EvaluationMetricEntity create(MetricWriteContext context, StandardMetricValue value) {
        // 校验上下文基础字段完整性
        requireContext(context);
        // 校验指标契约：metricKey、值类型等规则
        MetricContractValidator.validate(context.evaluatorKey(), value);
        // 校验生产者身份，区分评估型/执行型Metric归属
        requireProducer(context, value.source());

        EvaluationMetricEntity entity = new EvaluationMetricEntity();
        // 雪花ID主键
        entity.setId(IdWorker.getId());
        // 空间、执行链路唯一标识
        entity.setSpaceId(context.spaceId());
        entity.setRunId(context.runId());
        entity.setCaseRunId(context.caseRunId());
        entity.setCaseAttemptId(context.caseAttemptId());
        entity.setTestCaseVersionId(context.testCaseVersionId());
        // 评估器相关ID
        entity.setEvaluationResultId(context.evaluationResultId());
        entity.setEvaluatorVersionId(context.evaluatorVersionId());
        // 指标契约版本，用于后续数据兼容
        entity.setContractVersion(METRIC_CONTRACT_VERSION);
        // 指标来源枚举名称
        entity.setSource(value.source().name());
        // 生产者ID
        entity.setProducerId(context.producerId());
        // 指标key
        entity.setMetricKey(value.metricKey());
        // 值类型枚举名称
        entity.setValueType(value.valueType().name());
        // 多类型值存储，按ValueType仅填充对应字段
        entity.setNumericValue(value.numericValue());
        entity.setBooleanValue(value.booleanValue());
        entity.setStringValue(value.stringValue());
        entity.setUnit(value.unit());
        // 指标优化方向（越大越好/越小越好）
        entity.setDirection(value.direction().name());
        return entity;
    }

    /**
     * 校验Metric写入上下文核心字段非空，保证基础链路信息完整
     * @param context 指标写入上下文
     */
    private static void requireContext(MetricWriteContext context) {
        if (context == null || context.spaceId() == null || context.runId() == null
                || context.caseRunId() == null || context.caseAttemptId() == null
                || context.testCaseVersionId() == null || context.producerId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Metric 写入上下文不完整");
        }
    }

    /**
     * 生产者身份校验，区分【评估型Metric】与【执行型Metric】两套归属规则
     * @param context 指标写入上下文
     * @param source 指标来源类型
     */
    private static void requireProducer(MetricWriteContext context, EvaluationMetricSource source) {
        // 评估器产出的Metric：producerId必须等于evaluationResultId，评估相关字段不可为空
        if (source == EvaluationMetricSource.EVALUATOR) {
            if (context.evaluationResultId() == null || context.evaluatorVersionId() == null
                    || !context.producerId().equals(context.evaluationResultId())
                    || context.evaluatorKey() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "评估型 Metric 生产者身份不一致");
            }
            return;
        }
        // 执行链路产出的Metric：不能携带评估结果信息，producerId必须等于caseAttemptId
        if (context.evaluationResultId() != null || context.evaluatorVersionId() != null
                || !context.producerId().equals(context.caseAttemptId())
                || context.evaluatorKey() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "执行型 Metric 生产者身份不一致");
        }
    }
}

