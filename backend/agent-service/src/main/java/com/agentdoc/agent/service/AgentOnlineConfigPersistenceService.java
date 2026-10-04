package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentOnlineConfigMapper;
import com.agentdoc.agent.pojo.entity.AgentOnlineConfigEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 两份模板同事务保存，失败不留下半份配置。 */
@Service
@RequiredArgsConstructor
public class AgentOnlineConfigPersistenceService {
    private final AgentOnlineConfigMapper mapper;

    @Transactional
    public void savePair(List<AgentOnlineConfigEntity> pair) {
        pair.forEach(mapper::insert);
    }
}
