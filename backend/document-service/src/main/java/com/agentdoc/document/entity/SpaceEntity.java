package com.agentdoc.document.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("space")
public class SpaceEntity extends BaseLogicDeleteEntity {

    private String name;

    private String description;

    private Long ownerId;

    private Long tokenBudget;

    private Integer status;
}
