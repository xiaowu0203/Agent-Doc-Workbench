package com.agentdoc.task.mapper;

import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务 Mapper。
 */
public interface TaskMapper extends BaseMapper<TaskEntity> {
    void insertBatch(@Param("entities") List<TaskEntity> missing);
}
