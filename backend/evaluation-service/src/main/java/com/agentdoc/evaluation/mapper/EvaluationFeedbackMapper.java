package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface EvaluationFeedbackMapper extends BaseMapper<EvaluationFeedbackEntity> {
    @Select("SELECT f.id FROM evaluation_feedback f JOIN online_assignment a ON a.id=f.online_assignment_id LEFT JOIN online_experiment_event e ON e.experiment_id=a.experiment_id AND e.assignment_id=a.id AND e.event_type='CONFIRMED_SAFETY_EVENT' WHERE f.source_type='MANUAL' AND f.serious_safety_event=1 AND e.id IS NULL ORDER BY f.id LIMIT 100")
    List<Long> pendingOnlineSafetyFeedback();
}
