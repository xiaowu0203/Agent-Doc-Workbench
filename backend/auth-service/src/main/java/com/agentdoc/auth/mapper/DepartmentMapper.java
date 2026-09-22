package com.agentdoc.auth.mapper;

import com.agentdoc.auth.pojo.entity.DepartmentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {
    List<DepartmentEntity> selectHierarchy();
}
