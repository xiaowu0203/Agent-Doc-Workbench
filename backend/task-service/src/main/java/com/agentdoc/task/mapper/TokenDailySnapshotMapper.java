package com.agentdoc.task.mapper;

import com.agentdoc.task.pojo.entity.TokenDailySnapshotEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Token 当日快照 Mapper。
 */
public interface TokenDailySnapshotMapper extends BaseMapper<TokenDailySnapshotEntity> {
    int insertBatch(@Param("entities") List<TokenDailySnapshotEntity> entities);
}
