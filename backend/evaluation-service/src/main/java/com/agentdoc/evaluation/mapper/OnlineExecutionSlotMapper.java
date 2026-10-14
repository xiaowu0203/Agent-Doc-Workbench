package com.agentdoc.evaluation.mapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExecutionSlotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface OnlineExecutionSlotMapper extends BaseMapper<OnlineExecutionSlotEntity> {
    @Select("SELECT * FROM online_execution_slot WHERE experiment_id=#{experimentId} AND variant=#{variant} AND slot_no=1 FOR UPDATE")
    OnlineExecutionSlotEntity lock(@Param("experimentId") Long experimentId, @Param("variant") String variant);
}
