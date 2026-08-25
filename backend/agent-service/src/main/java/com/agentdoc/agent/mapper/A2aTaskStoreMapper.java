package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.A2aTaskStoreEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface A2aTaskStoreMapper extends BaseMapper<A2aTaskStoreEntity> {

    int upsert(A2aTaskStoreEntity entity);
}
