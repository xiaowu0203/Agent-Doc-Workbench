package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentOnlineOriginalTextEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 原始文本只插入和读取；服务不提供更新入口。 */
public interface AgentOnlineOriginalTextMapper extends BaseMapper<AgentOnlineOriginalTextEntity> { }
