package com.agentdoc.evaluation.metric;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricContractValidatorTest {

    @Test
    void catalogUsesStableKeysAndExplicitSemantics() {
        assertThat(MetricDefinitionCatalog.definitions()).hasSize(15).allSatisfy(definition -> {
            assertThat(definition.metricKey()).matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");
            assertThat(definition.valueType()).isNotNull();
            assertThat(definition.unit()).isNotBlank();
            assertThat(definition.direction()).isNotNull();
            assertThat(definition.source()).isNotNull();
        });
    }

    @Test
    void evaluatorCanOnlyProduceItsDeclaredMetric() {
        StandardMetricValue metric = StandardMetricValue.bool(
                "evaluation.document-change.valid", true,
                EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EVALUATOR);

        assertThat(MetricContractValidator.validate("document-change-validator", metric).metricKey())
                .isEqualTo(metric.metricKey());
        assertThatThrownBy(() -> MetricContractValidator.validate("artifact-contract", metric))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executionCostRequiresRealUppercaseCurrencyUnit() {
        StandardMetricValue usd = StandardMetricValue.number("execution.cost", new BigDecimal("0.015"), "USD",
                EvaluationMetricDirection.LOWER_IS_BETTER, EvaluationMetricSource.EXECUTION);

        assertThat(MetricContractValidator.validate(null, usd).currencyUnit()).isTrue();
        assertThatThrownBy(() -> MetricContractValidator.validate(null,
                StandardMetricValue.number("execution.cost", BigDecimal.ONE, "usd",
                        EvaluationMetricDirection.LOWER_IS_BETTER, EvaluationMetricSource.EXECUTION)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void typedValueMustMatchValueTypeAndBeExclusive() {
        StandardMetricValue invalid = new StandardMetricValue("execution.success",
                EvaluationMetricValueType.BOOLEAN, BigDecimal.ONE, true, null, "boolean",
                EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EXECUTION);

        assertThatThrownBy(() -> MetricContractValidator.validate(null, invalid))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ratioCannotExceedOne() {
        StandardMetricValue invalid = StandardMetricValue.number("evaluation.text-assertion.pass-ratio",
                new BigDecimal("1.01"), "ratio", EvaluationMetricDirection.HIGHER_IS_BETTER,
                EvaluationMetricSource.EVALUATOR);

        assertThatThrownBy(() -> MetricContractValidator.validate("text-assertion", invalid))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void factoryBindsExecutionMetricToAttemptProducer() {
        MetricWriteContext context = new MetricWriteContext(1L, 2L, 3L, 4L, 5L,
                null, null, 4L, null);

        EvaluationMetricEntity entity = EvaluationMetricFactory.create(context,
                StandardMetricValue.number("execution.total-tokens", new BigDecimal("120"), "token",
                        EvaluationMetricDirection.LOWER_IS_BETTER, EvaluationMetricSource.EXECUTION));

        assertThat(entity.getContractVersion()).isEqualTo(1);
        assertThat(entity.getProducerId()).isEqualTo(4L);
        assertThat(entity.getCaseAttemptId()).isEqualTo(4L);
        assertThat(entity.getEvaluationResultId()).isNull();
        assertThat(entity.getNumericValue()).isEqualByComparingTo("120");
    }

    @Test
    void factoryRejectsExecutionMetricWithResultProducer() {
        MetricWriteContext invalid = new MetricWriteContext(1L, 2L, 3L, 4L, 5L,
                6L, 7L, 6L, null);

        assertThatThrownBy(() -> EvaluationMetricFactory.create(invalid,
                StandardMetricValue.bool("execution.success", true,
                        EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EXECUTION)))
                .isInstanceOf(BusinessException.class);
    }
}
