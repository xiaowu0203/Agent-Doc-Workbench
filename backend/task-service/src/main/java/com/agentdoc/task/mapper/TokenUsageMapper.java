package com.agentdoc.task.mapper;

import com.agentdoc.task.pojo.entity.TokenUsageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;

/**
 * Token 用量 Mapper。
 */
public interface TokenUsageMapper extends BaseMapper<TokenUsageEntity> {

    int deleteByUsageDate(@Param("usageDate") LocalDate usageDate);

    int insertBatch(@Param("entities") List<TokenUsageEntity> entities);
}
