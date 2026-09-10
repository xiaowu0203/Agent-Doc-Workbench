package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 修改平台用户状态请求。
 */
@Schema(description = "修改平台用户状态请求")
public record PlatformUserStatusUpdateDTO(
        @NotNull(message = "用户状态不能为空")
        @Min(value = 0, message = "用户状态无效")
        @Max(value = 1, message = "用户状态无效")
        @Schema(description = "状态：0 禁用 / 1 启用")
        Integer status
) {
}
