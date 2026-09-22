package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 评估用例运行视图对象
 * <p>
 * 代表一轮评估运行内单个测试用例版本的执行记录，包含当前尝试、回放任务与人工反馈列表。
 */
@Schema(description = "评估用例运行")
public record EvaluationCaseRunVO(
        @Schema(description = "用例运行ID")
        Long id,

        @Schema(description = "测试用例版本ID")
        Long testCaseVersionId,

        @Schema(description = "用例运行状态")
        String status,

        @Schema(description = "当前执行尝试ID")
        Long currentAttemptId,

        @Schema(description = "回放任务ID")
        Long replayTaskId,

        @Schema(description = "尝试序号，从1开始递增")
        Integer attemptNo,

        @Schema(description = "关联的人工反馈列表")
        List<EvaluationFeedbackVO> feedback
) {
    /**
     * 实体转VO，不带反馈数据
     * @param caseRun 用例运行实体
     * @param attempt 当前尝试实体
     * @return 视图对象（反馈为空集合）
     */
    public static EvaluationCaseRunVO from(EvaluationCaseRunEntity caseRun,
                                           EvaluationCaseAttemptEntity attempt) {
        return new EvaluationCaseRunVO(caseRun.getId(), caseRun.getTestCaseVersionId(), caseRun.getStatus(),
                caseRun.getCurrentAttemptId(), attempt.getReplayTaskId(), attempt.getAttemptNo(), List.of());
    }

    /**
     * 实体转VO，携带人工反馈列表
     * @param caseRun 用例运行实体
     * @param attempt 当前尝试实体
     * @param feedback 人工反馈VO列表
     * @return 视图对象（反馈列表做不可变拷贝）
     */
    public static EvaluationCaseRunVO from(EvaluationCaseRunEntity caseRun,
                                           EvaluationCaseAttemptEntity attempt,
                                           List<EvaluationFeedbackVO> feedback) {
        return new EvaluationCaseRunVO(caseRun.getId(), caseRun.getTestCaseVersionId(), caseRun.getStatus(),
                caseRun.getCurrentAttemptId(), attempt.getReplayTaskId(), attempt.getAttemptNo(),
                List.copyOf(feedback));
    }
}