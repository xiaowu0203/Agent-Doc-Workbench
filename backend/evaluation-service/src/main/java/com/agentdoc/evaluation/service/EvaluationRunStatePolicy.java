package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;

import java.util.List;

/**
 * EvaluationRun 状态聚合的唯一实现。
 * 根据所有子Attempt状态、暂停标记、取消请求标记，聚合计算顶层EvaluationRun状态，是Run状态的单一可信源。
 */
public final class EvaluationRunStatePolicy {

    // 工具类，禁止实例化
    private EvaluationRunStatePolicy() {
    }

    /**
     * 聚合计算Run顶层状态
     * @param attempts 所有CaseAttempt的状态列表
     * @param paused 是否标记暂停
     * @param cancelRequested 是否标记请求取消
     * @return 聚合后的 EvaluationRunStatus
     *
     * 状态优先级规则（从高到低）：
     * 1. paused=true → PAUSED（最高优先级，一旦暂停直接覆盖）
     * 2. 存在活跃Attempt：
     *    - 已请求取消 → CANCEL_PENDING
     *    - 未请求取消 → RUNNING
     * 3. 无任何活跃Attempt（全部完成）：
     *    - 已请求取消 → CANCELED
     *    - 存在Replay失败 / Evaluator失败 → COMPLETED_WITH_ERRORS
     *    - 全部成功 → COMPLETED
     */
    public static EvaluationRunStatus aggregate(List<EvaluationAttemptStatus> attempts,
                                                boolean paused, boolean cancelRequested) {
        // 优先级最高：暂停标记
        if (paused) {
            return EvaluationRunStatus.PAUSED;
        }

        // 存在正在运行的子任务
        if (attempts.stream().anyMatch(EvaluationAttemptStatus::active)) {
            return cancelRequested ? EvaluationRunStatus.CANCEL_PENDING : EvaluationRunStatus.RUNNING;
        }

        // 所有子任务都结束，并且用户发起过取消请求
        if (cancelRequested) {
            return EvaluationRunStatus.CANCELED;
        }

        // 全部子任务结束，判断是否存在失败项
        boolean infrastructureError = attempts.stream().anyMatch(status ->
                status == EvaluationAttemptStatus.REPLAY_FAILED
                        || status == EvaluationAttemptStatus.EVALUATOR_FAILED);

        return infrastructureError ? EvaluationRunStatus.COMPLETED_WITH_ERRORS : EvaluationRunStatus.COMPLETED;
    }
}
