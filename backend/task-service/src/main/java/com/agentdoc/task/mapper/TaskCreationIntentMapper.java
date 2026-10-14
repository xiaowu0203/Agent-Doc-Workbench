package com.agentdoc.task.mapper;

import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface TaskCreationIntentMapper extends BaseMapper<TaskCreationIntentEntity> {
    @Select("SELECT * FROM task_creation_intent WHERE id=#{id} FOR UPDATE")
    TaskCreationIntentEntity lock(Long id);
}
