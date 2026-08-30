package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.enums.SkillSelectionMode;
import org.springframework.stereotype.Component;

/**
 * 全部绑定技能选择策略
 * <p>
 * 实现 {@link SkillSelectionStrategy}，对应策略模式：{@link SkillSelectionMode#ALL_BOUND}。
 * 策略规则：直接选用当前Agent已经绑定的全部技能，不做过滤、不做LLM动态挑选。
 * Agent执行时，把所有已绑定的MCP/Skill全部暴露给模型，模型自主决定调用哪些工具。
 * </p>
 */
@Component
public class AllBoundSkillSelectionStrategy implements SkillSelectionStrategy {

    /**
     * 获取本策略对应的选择模式枚举
     * @return ALL_BOUND 模式标识
     */
    @Override
    public SkillSelectionMode mode() {
        return SkillSelectionMode.ALL_BOUND;
    }

    /**
     * 执行技能选择逻辑
     * <p>直接取上下文里 Agent 的全部绑定技能集合作为选中结果；无过滤、无候选集。</p>
     * @param context 技能选择上下文，携带当前Agent绑定的技能列表等信息
     * @return 选择结果：模式标识 + 全部绑定技能列表，候选技能字段置为null
     */
    @Override
    public SkillSelectionResult select(SkillSelectionContext context) {
        return new SkillSelectionResult(SkillSelectionMode.ALL_BOUND.name(), context.boundSkills(), null);
    }
}
