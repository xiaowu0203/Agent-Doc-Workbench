package com.agentdoc.document.pojo.dto;

import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.pojo.entity.MemberEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 添加空间成员请求参数。
 */
@Schema(description = "添加空间成员请求")
public record MemberAddDTO(

        @Schema(description = "用户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "用户 ID 不能为空")
        Long userId,

        @Schema(description = "成员角色", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "成员角色不能为空")
        SpaceRole role
) {

    /**
     * 转换为成员实体。
     * @param spaceId 空间 ID
     * @return 成员实体
     */
    public MemberEntity toEntity(Long spaceId) {
        MemberEntity entity = new MemberEntity();
        entity.setSpaceId(spaceId);
        entity.setUserId(userId);
        entity.setRole(role.getCode());
        return entity;
    }
}
