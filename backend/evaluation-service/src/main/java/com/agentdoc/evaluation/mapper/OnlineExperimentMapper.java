package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OnlineExperimentMapper extends BaseMapper<OnlineExperimentEntity> {
    @Select("SELECT * FROM online_experiment WHERE id=#{id} FOR UPDATE")
    OnlineExperimentEntity lock(Long id);
    @Select("<script>SELECT * FROM online_experiment WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY id FOR UPDATE</script>")
    List<OnlineExperimentEntity> lockAll(@Param("ids") List<Long> ids);
}
