package com.agentdoc.auth.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户平台角色绑定实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_platform_role")
@Schema(description = "用户平台角色绑定实体")
public class UserPlatformRoleEntity extends BaseEntity {

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "平台角色 ID")
    private Long roleId;
}
