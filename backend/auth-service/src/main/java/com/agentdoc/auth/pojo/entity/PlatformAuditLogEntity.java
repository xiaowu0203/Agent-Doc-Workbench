package com.agentdoc.auth.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台管理操作使用的追加型审计日志实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
@Schema(description = "平台管理审计日志实体")
public class PlatformAuditLogEntity extends BaseEntity {

    @Schema(description = "空间 ID；平台操作为空")
    private Long spaceId;

    @Schema(description = "关联任务 ID；平台操作为空")
    private Long taskId;

    @Schema(description = "主体类型：1 人")
    private Integer actorType;

    @Schema(description = "操作人用户 ID")
    private Long actorId;

    @Schema(description = "操作行为")
    private String action;

    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "目标 ID")
    private Long targetId;

    @Schema(description = "脱敏操作详情 JSON")
    private String detail;

    @Schema(description = "来源 IP")
    private String ip;

    @Schema(description = "链路追踪 ID")
    private String traceId;
}
