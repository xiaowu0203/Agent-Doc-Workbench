package com.agentdoc.evaluation.pojo.vo;

import java.util.List;

/** Experiment 启动前动态预检结果。 */
public record ExperimentPreflightVO(Long experimentId, int caseCount, int variantCount,
                                    long plannedTaskCount, long plannedTokenBudget,
                                    boolean eligible, List<ExperimentPreflightIssueVO> issues) {
    public ExperimentPreflightVO {
        issues = List.copyOf(issues);
    }
}
