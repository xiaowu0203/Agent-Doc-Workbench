package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建目录请求参数。
 */
@Schema(description = "创建目录请求")
public record DirectoryCreateDTO(

        @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "空间 ID 不能为空")
        Long spaceId,

        @Schema(description = "父目录 ID；为空表示根目录")
        Long parentId,

        @Schema(description = "目录名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "目录名称不能为空")
        @Size(max = 200, message = "目录名称最长 200 字符")
        String title
) {
}
