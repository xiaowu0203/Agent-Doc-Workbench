package com.agentdoc.task.service;

import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.mapper.AuditLogMapper;
import com.agentdoc.task.pojo.entity.AuditLogEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 追加型审计日志服务。业务代码只允许通过 insert 写入，不提供修改和删除能力。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public void recordHuman(Long spaceId, AuditAction action, AuditTargetType targetType,
                            Long targetId, String detail) {
        record(spaceId, null, ActorType.HUMAN, AuthUtils.getUserIdOrException(),
                action, targetType, targetId, detail);
    }

    public void recordAgent(Long spaceId, Long taskId, Long agentId, AuditAction action,
                            AuditTargetType targetType, Long targetId, String detail) {
        record(spaceId, taskId, ActorType.AGENT, agentId, action, targetType, targetId, detail);
    }

    private void record(Long spaceId, Long taskId, ActorType actorType, Long actorId, AuditAction action,
                        AuditTargetType targetType, Long targetId, String detail) {
        auditLogMapper.insert(AuditLogEntity.create(
                spaceId, taskId, actorType, actorId, action, targetType, targetId, detail));
    }

    /** 按创建时间读取指定变更请求的追加型轨迹。 */
    public List<AuditLogEntity> listChangeRequestTrail(Long changeRequestId) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getTargetType, AuditTargetType.CHANGE_REQUEST.getCode())
                .eq(AuditLogEntity::getTargetId, changeRequestId)
                .orderByAsc(AuditLogEntity::getCreatedAt));
    }
}
