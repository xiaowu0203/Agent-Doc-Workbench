package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.PlatformAuditLogMapper;
import com.agentdoc.auth.pojo.entity.PlatformAuditLogEntity;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.agentdoc.auth.constant.PlatformManagementConstant.HUMAN_ACTOR_TYPE;

/**
 * 平台管理操作审计写入服务。
 */
@Service
@RequiredArgsConstructor
public class PlatformAuditLogService {

    private final PlatformAuditLogMapper auditLogMapper;

    /**
     * 写入不包含秘密值的追加型平台审计记录。
     */
    public void record(String action, String targetType, Long targetId, Map<String, ?> detail) {
        PlatformAuditLogEntity entity = new PlatformAuditLogEntity();
        entity.setActorType(HUMAN_ACTOR_TYPE);
        entity.setActorId(AuthUtils.getUserIdOrException());
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(detail == null || detail.isEmpty() ? null : JsonUtils.toJson(detail));
        auditLogMapper.insert(entity);
    }
}
