package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新目录请求参数。
 */
@Schema(description = "更新目录请求")
public record DirectoryUpdateDTO(

        @Schema(description = "目录名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "目录名称不能为空")
        @Size(max = 200, message = "目录名称最长 200 字符")
        String title
) {
}
