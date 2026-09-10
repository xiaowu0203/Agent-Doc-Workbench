package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 移动文档请求参数。
 */
@Schema(description = "移动文档请求")
public record DocumentMoveDTO(

        @Schema(description = "目标目录 ID，null 为空间根层")
        Long directoryId
) {
}
