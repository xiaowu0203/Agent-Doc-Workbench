package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.OnlineEvaluationAttemptEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface OnlineEvaluationAttemptMapper extends BaseMapper<OnlineEvaluationAttemptEntity> {
    @Select("SELECT COALESCE(MAX(attempt_no),0) FROM online_evaluation_attempt WHERE assignment_id=#{assignmentId}")
    int lastAttempt(Long assignmentId);
}
