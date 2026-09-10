package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_PLATFORM_ROLE_COUNT;

/**
 * 替换用户平台角色请求。
 */
@Schema(description = "替换用户平台角色请求")
public record PlatformUserRoleReplaceDTO(
        @NotNull(message = "平台角色集合不能为空")
        @Size(max = MAX_PLATFORM_ROLE_COUNT, message = "当前仅支持平台超级管理员角色")
        @Schema(description = "平台角色稳定标识集合；空数组表示移除平台角色")
        List<String> roleKeys
) {
}
