package com.agentdoc.agent.convertor;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.SkillVO;

/**
 * Skill实体转换器
 * <p>完成数据库{@link SkillEntity}实体转为前端展示VO，附加统计版本数量</p>
 */
public final class SkillConvertor {

    private SkillConvertor() {
    }

    /**
     * Skill数据库实体转换为视图VO
     *
     * @param entity      Skill主表数据库实体，非null
     * @param versionCount 该Skill下拥有的版本总数量（统计值，来自查询count）
     * @return 组装完成的 {@link SkillVO}
     */
    public static SkillVO toVO(SkillEntity entity, long versionCount) {
        return new SkillVO(entity.getId(), entity.getSpaceId(), entity.getName(), entity.getDisplayName(),
                entity.getDescription(),
                SkillStatus.fromCode(entity.getStatus()), versionCount, entity.getCreatedBy(), entity.getCreatedAt());
    }
}
