package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 变更请求追加型操作轨迹。 */
@Schema(description = "变更请求操作轨迹")
public record ChangeRequestAuditVO(
        Long id,
        ActorType actorType,
        Long actorId,
        String actorName,
        AuditAction action,
        String detail,
        String traceId,
        LocalDateTime createdAt) {
}
