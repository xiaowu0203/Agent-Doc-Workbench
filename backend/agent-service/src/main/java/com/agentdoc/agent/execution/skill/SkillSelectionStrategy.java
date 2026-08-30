package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.enums.SkillSelectionMode;

/**
 * Agent 级 Skill 选择策略。
 */
public interface SkillSelectionStrategy {

    /**
     * @return 当前策略对应的 Agent 配置模式
     */
    SkillSelectionMode mode();

    /**
     * @param context 本次执行冻结的选择上下文
     * @return Skill 选择结果
     */
    SkillSelectionResult select(SkillSelectionContext context);
}
