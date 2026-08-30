package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.SkillAuditLogMapper;
import com.agentdoc.agent.pojo.entity.SkillAuditLogEntity;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 技能模块操作审计日志服务
 * <p>
 * 记录空间内技能相关的人工操作审计流水，用于行为追溯、操作留痕。
 * 当前仅支持人类操作者，自动从上下文获取登录用户ID；详情对象序列化为JSON持久化。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SkillAuditLogService {

    /** 操作者类型：人类用户 */
    private static final int HUMAN_ACTOR = 1;

    private final SkillAuditLogMapper mapper;

    /**
     * 写入一条技能模块操作审计日志
     *
     * @param spaceId    所属空间ID
     * @param action     操作行为标识，例如 create / update / delete / import 等
     * @param targetType 操作目标实体类型，如 skill_package、skill_version
     * @param targetId   被操作目标实体ID
     * @param detail     操作扩展详情，支持复杂业务对象；传null则数据库detail字段置null
     */
    public void record(Long spaceId, String action, String targetType, Long targetId, Map<String, ?> detail) {
        SkillAuditLogEntity entity = new SkillAuditLogEntity();
        entity.setSpaceId(spaceId);
        entity.setActorType(HUMAN_ACTOR);
        entity.setActorId(AuthUtils.getUserIdOrException());
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(detail == null ? null : JsonUtils.toJson(detail));
        mapper.insert(entity);
    }
}
