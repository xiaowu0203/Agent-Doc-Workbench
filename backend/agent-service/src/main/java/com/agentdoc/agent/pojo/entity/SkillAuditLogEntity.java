package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Skill 操作追加型审计记录；仅允许 INSERT。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class SkillAuditLogEntity extends BaseEntity {
    @Schema(description = "空间 ID")
    private Long spaceId;
    @Schema(description = "操作者类型")
    private Integer actorType;
    @Schema(description = "操作者用户 ID")
    private Long actorId;
    @Schema(description = "审计动作")
    private String action;
    @Schema(description = "目标类型")
    private String targetType;
    @Schema(description = "目标 ID")
    private Long targetId;
    @Schema(description = "审计详情 JSON")
    private String detail;
}
