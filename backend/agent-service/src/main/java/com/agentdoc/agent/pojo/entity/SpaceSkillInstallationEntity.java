package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("space_skill_installation")
@Schema(description = "空间安装的系统 Skill")
public class SpaceSkillInstallationEntity extends BaseEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;
    @Schema(description = "系统 Skill ID")
    private Long skillId;
    @Schema(description = "当前固定的 Skill 版本 ID")
    private Long skillVersionId;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "安装人用户 ID")
    private Long installedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
