package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间角色实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("space_role")
@Schema(description = "空间角色实体")
public class SpaceRoleEntity extends BaseLogicDeleteEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "空间内稳定角色标识")
    private String roleKey;

    @Schema(description = "角色展示名称")
    private String displayName;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "是否为受保护角色")
    private Boolean protectedRole;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;
}
