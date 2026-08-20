package com.agentdoc.task.entity;

import com.agentdoc.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计日志实体。
 * 注意：本表为追加型日志表，无 deleted / updated_at 列，继承 {@link BaseEntity}（id/createdAt）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class AuditLogEntity extends BaseEntity {

    private Long spaceId;

    /** 主体类型：1 人 / 2 Agent */
    private Integer actorType;

    private Long actorId;

    private String action;

    private String targetType;

    private Long targetId;

    /** 操作详情（JSON） */
    private String detail;

    private String ip;

    private String traceId;
}
