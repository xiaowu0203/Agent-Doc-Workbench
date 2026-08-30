package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.execution.context.ExecutionSnapshotCopies;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;

import java.util.List;

/**
 * Skill 选择阶段的不可变输入上下文。
 *
 * @param instruction 用户任务指令
 * @param agent       Agent 配置快照
 * @param model       Agent 主模型配置快照
 * @param boundSkills 当前 Agent 绑定的 Skill 候选
 */
public record SkillSelectionContext(
        String instruction,
        AgentEntity agent,
        ModelEntity model,
        List<SkillCandidate> boundSkills) {
    public SkillSelectionContext {
        agent = ExecutionSnapshotCopies.agent(agent);
        model = ExecutionSnapshotCopies.model(model);
        boundSkills = List.copyOf(boundSkills);
    }

    @Override
    public AgentEntity agent() {
        return ExecutionSnapshotCopies.agent(agent);
    }

    @Override
    public ModelEntity model() {
        return ExecutionSnapshotCopies.model(model);
    }
}
