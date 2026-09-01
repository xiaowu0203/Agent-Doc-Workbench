package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 移动目录请求参数。
 */
@Schema(description = "移动目录请求")
public record DirectoryMoveDTO(

        @Schema(description = "目标父目录 ID，null 表示空间根层")
        Long parentId
) {
}
