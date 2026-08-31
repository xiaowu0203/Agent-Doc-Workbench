package com.agentdoc.auth.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台角色实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_role")
@Schema(description = "平台角色实体")
public class PlatformRoleEntity extends BaseLogicDeleteEntity {

    @Schema(description = "平台角色稳定技术标识")
    private String roleKey;

    @Schema(description = "平台角色展示名称")
    private String displayName;

    @Schema(description = "是否为受保护角色")
    private Boolean protectedRole;
}
