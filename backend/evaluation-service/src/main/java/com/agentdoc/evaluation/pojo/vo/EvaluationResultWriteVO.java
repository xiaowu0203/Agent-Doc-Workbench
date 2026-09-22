package com.agentdoc.evaluation.pojo.vo;

import java.util.List;

/** Result 原子追加后的不可变业务身份。 */
public record EvaluationResultWriteVO(
        Long resultId,
        String status,
        List<Long> metricIds,
        List<Long> evidenceReferenceIds) {
}
