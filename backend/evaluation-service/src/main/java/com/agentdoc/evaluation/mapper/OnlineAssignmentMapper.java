package com.agentdoc.evaluation.mapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineParticipationVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface OnlineAssignmentMapper extends BaseMapper<OnlineAssignmentEntity> {
    @Select("SELECT variant FROM online_assignment WHERE experiment_id=#{experimentId} AND binding_schema_version=2 GROUP BY document_id,variant")
    List<String> runtimeUnits(Long experimentId);
    @Select("SELECT * FROM online_assignment WHERE task_id=#{taskId} FOR UPDATE")
    OnlineAssignmentEntity lockTask(Long taskId);
    @Select({"<script>SELECT experiment_id,COUNT(DISTINCT document_id) AS document_count FROM online_assignment",
            "WHERE binding_schema_version=2 AND experiment_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY experiment_id</script>"})
    List<OnlineParticipationVO> participation(@Param("ids") List<Long> ids);
}
