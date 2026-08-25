package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.A2aPushConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface A2aPushConfigMapper extends BaseMapper<A2aPushConfigEntity> {

    int upsert(A2aPushConfigEntity entity);
}
