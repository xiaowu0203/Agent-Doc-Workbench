package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EvaluationFeedbackMapper extends BaseMapper<EvaluationFeedbackEntity> {
    @Select("<script>SELECT DISTINCT online_assignment_id FROM evaluation_feedback "
            + "WHERE space_id=#{spaceId} AND created_by=#{actorId} AND source_type='MANUAL' "
            + "AND rubric_score IS NOT NULL AND online_assignment_id IN "
            + "<foreach collection='assignmentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> ownOnlineReviewedAssignments(@Param("spaceId") Long spaceId, @Param("actorId") Long actorId,
                                           @Param("assignmentIds") List<Long> assignmentIds);

    @Select("SELECT f.id FROM evaluation_feedback f JOIN online_assignment a ON a.id=f.online_assignment_id LEFT JOIN online_experiment_event e ON e.experiment_id=a.experiment_id AND e.assignment_id=a.id AND e.event_type='CONFIRMED_SAFETY_EVENT' WHERE f.source_type='MANUAL' AND f.serious_safety_event=1 AND e.id IS NULL ORDER BY f.id LIMIT 100")
    List<Long> pendingOnlineSafetyFeedback();
}
