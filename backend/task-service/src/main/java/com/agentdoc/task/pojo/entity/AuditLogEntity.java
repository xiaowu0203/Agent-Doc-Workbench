package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计日志实体。
 * 注意：本表为追加型日志表，无 deleted / updated_at 列，继承 {@link BaseEntity}（id/createdAt）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
@Schema(description = "审计日志实体")
public class AuditLogEntity extends BaseEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "关联任务 ID")
    private Long taskId;

    @Schema(description = "主体类型：1 人 / 2 Agent")
    private Integer actorType;

    @Schema(description = "主体 ID")
    private Long actorId;

    @Schema(description = "操作行为")
    private String action;

    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "目标 ID")
    private Long targetId;

    @Schema(description = "操作详情（JSON）")
    private String detail;

    @Schema(description = "来源 IP")
    private String ip;

    @Schema(description = "链路追踪 ID")
    private String traceId;

    /**
     * 创建追加型审计日志实体。
     */
    public static AuditLogEntity create(Long spaceId, Long taskId, ActorType actorType, Long actorId,
                                        AuditAction action, AuditTargetType targetType,
                                        Long targetId, String detail) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setSpaceId(spaceId);
        entity.setTaskId(taskId);
        entity.setActorType(actorType.getCode());
        entity.setActorId(actorId);
        entity.setAction(action.name());
        entity.setTargetType(targetType.getCode());
        entity.setTargetId(targetId);
        entity.setDetail(detail);
        return entity;
    }
}
