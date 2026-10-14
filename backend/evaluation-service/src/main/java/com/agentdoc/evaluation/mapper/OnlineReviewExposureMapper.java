package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.OnlineReviewExposureEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface OnlineReviewExposureMapper extends BaseMapper<OnlineReviewExposureEntity> {
    @Insert("<script>INSERT INTO online_review_exposure(id,experiment_id,space_id,actor_id,source,captured_at,source_hash) VALUES <foreach collection='rows' item='row' separator=','>(#{row.id},#{row.experimentId},#{row.spaceId},#{row.actorId},#{row.source},#{row.capturedAt},#{row.sourceHash})</foreach></script>")
    int insertAll(@Param("rows") List<OnlineReviewExposureEntity> rows);
}
