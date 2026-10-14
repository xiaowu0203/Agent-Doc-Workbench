package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentCreateIntentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentCreateIntentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 不在事务内执行跨服务捕获；意图身份与最终保存分开。 */
@Service
@RequiredArgsConstructor
public class OnlineExperimentPersistenceService {
    private final OnlineExperimentMapper mapper;
    private final OnlineExperimentCreateIntentMapper intentMapper;

    @Transactional
    public void reserve(OnlineExperimentCreateIntentEntity intent) { intentMapper.insert(intent); }

    @Transactional
    public void complete(OnlineExperimentEntity experiment) {
        mapper.insert(experiment);
        var intent = new OnlineExperimentCreateIntentEntity();
        intent.setId(experiment.getId());
        intent.setStatus("CREATED");
        intentMapper.updateById(intent);
    }
}
