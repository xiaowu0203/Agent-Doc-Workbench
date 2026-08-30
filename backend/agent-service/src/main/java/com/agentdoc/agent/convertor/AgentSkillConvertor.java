package com.agentdoc.agent.convertor;

import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.vo.AgentSkillBindingVO;

/**
 * Agent‑Skill绑定关系转换器
 * <p>用于把数据库实体（绑定关系、Skill主表、Skill版本）转换为对外展示VO对象</p>
 */
public final class AgentSkillConvertor {

    private AgentSkillConvertor() {
    }

    /**
     * 将AgentSkill绑定关联实体 + Skill实体 + Skill版本实体，转换为视图VO
     *
     * @param relation Agent与Skill的绑定关系数据库实体 {@link AgentSkillEntity}
     * @param skill    Skill主表实体，允许null
     * @param version  Skill版本实体，允许null
     * @return 组装完成的 {@link AgentSkillBindingVO}；skill/version为null时对应字段填充null
     */
    public static AgentSkillBindingVO toVO(AgentSkillEntity relation, SkillEntity skill,
                                           SkillVersionEntity version) {
        return new AgentSkillBindingVO(relation.getId(), relation.getAgentId(), relation.getSkillId(),
                skill == null ? null : skill.getName(), relation.getSkillVersionId(),
                version == null ? null : version.getVersionNo(),
                version == null ? null : version.getSha256(), relation.getEnabled());
    }
}
