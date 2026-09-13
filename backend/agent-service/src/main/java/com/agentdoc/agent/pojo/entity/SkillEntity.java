package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill")
@Schema(description = "Skill 元数据")
public class SkillEntity extends BaseLogicDeleteEntity {

    @Schema(description = "作用域：SYSTEM / SPACE")
    private String scopeType;
    @Schema(description = "空间 ID")
    private Long spaceId;
    @Schema(description = "Skill 名称")
    private String name;
    @Schema(description = "Skill 展示名称")
    private String displayName;
    @Schema(description = "Skill 描述")
    private String description;
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
    @Schema(description = "下一个版本号")
    private Integer nextVersionNo;
    @Schema(description = "创建人用户 ID")
    private Long createdBy;
    @TableField(exist = false)
    @Schema(description = "空间内系统 Skill 安装记录 ID，仅空间可见列表使用")
    private Long installationId;
    @TableField(exist = false)
    @Schema(description = "空间当前固定的系统 Skill 版本 ID，仅空间可见列表使用")
    private Long installedVersionId;
}
