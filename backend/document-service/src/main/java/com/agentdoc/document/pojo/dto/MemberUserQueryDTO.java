package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 查询空间成员用户信息请求参数。
 */
@Schema(description = "空间成员用户信息查询请求")
public record MemberUserQueryDTO(

        @Schema(description = "用户 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "用户 ID 列表不能为空")
        @Size(max = 100, message = "单次最多查询 100 个用户")
        List<@NotNull(message = "用户 ID 不能为空") Long> userIds
) {
}
