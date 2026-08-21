package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("space")
@Schema(description = "空间实体")
public class SpaceEntity extends BaseLogicDeleteEntity {

    @Schema(description = "空间名称")
    private String name;

    @Schema(description = "空间描述")
    private String description;

    @Schema(description = "所有者用户 ID")
    private Long ownerId;

    @Schema(description = "Token 预算上限")
    private Long tokenBudget;

    @Schema(description = "状态：0 禁用 / 1 正常")
    private Integer status;
}
