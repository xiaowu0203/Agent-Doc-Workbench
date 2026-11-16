package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.PlatformAuditLogMapper;
import com.agentdoc.auth.pojo.entity.PlatformAuditLogEntity;
import com.agentdoc.common.context.TraceContext;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

import static com.agentdoc.auth.constant.PlatformManagementConstant.HUMAN_ACTOR_TYPE;
import static com.agentdoc.auth.constant.LocalIdentityConstant.AUTHENTICATION_SYSTEM_ACTOR;
import static com.agentdoc.auth.constant.LocalIdentityConstant.AUTHENTICATION_SYSTEM_ID;
import static com.agentdoc.auth.constant.LocalIdentityConstant.AUDIT_TARGET;

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
        append(action, targetType, targetId, HUMAN_ACTOR_TYPE, AuthUtils.getUserIdOrException(), detail);
    }

    /** 不要求匿名/机器登录伪装成用户；调用方只能传入已验证的人类ID或系统空值。 */
    public void recordAuthentication(String action, Long confirmedUserId, Map<String, ?> detail) {
        append(action, AUDIT_TARGET, confirmedUserId, confirmedUserId == null ? AUTHENTICATION_SYSTEM_ACTOR : HUMAN_ACTOR_TYPE,
                confirmedUserId == null ? AUTHENTICATION_SYSTEM_ID : confirmedUserId, detail);
    }

    private void append(String action, String targetType, Long targetId, int actorType, Long actorId, Map<String, ?> detail) {
        PlatformAuditLogEntity entity = new PlatformAuditLogEntity();
        entity.setActorType(actorType);
        entity.setActorId(actorId);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(detail == null || detail.isEmpty() ? null : JsonUtils.toJson(detail));
        String traceId = TraceContext.get();
        entity.setTraceId(traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId);
        auditLogMapper.insert(entity);
    }
}
