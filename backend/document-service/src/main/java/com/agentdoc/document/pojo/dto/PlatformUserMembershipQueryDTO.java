package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 平台管理侧批量查询用户空间参与情况请求。
 */
@Schema(description = "平台用户空间参与情况批量查询请求")
public record PlatformUserMembershipQueryDTO(
        @NotEmpty(message = "用户 ID 列表不能为空")
        @Size(max = 100, message = "单次最多查询 100 个用户")
        @Schema(description = "用户 ID 列表")
        List<@NotNull(message = "用户 ID 不能为空") Long> userIds
) {
}
