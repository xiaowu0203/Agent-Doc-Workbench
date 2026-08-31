package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 修改空间成员角色请求参数。
 */
@Schema(description = "修改成员角色请求")
public record MemberRoleUpdateDTO(

        @Schema(description = "新空间角色 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "角色不能为空")
        Long roleId
) {
}
