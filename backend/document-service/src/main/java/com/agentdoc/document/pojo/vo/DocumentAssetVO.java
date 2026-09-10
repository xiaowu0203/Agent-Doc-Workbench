package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档图片附件信息。
 */
@Schema(description = "文档图片附件")
public record DocumentAssetVO(

        @Schema(description = "附件 ID")
        Long id,

        @Schema(description = "文档 ID")
        Long documentId,

        @Schema(description = "原始文件名")
        String originalName,

        @Schema(description = "文件 MIME 类型")
        String contentType,

        @Schema(description = "文件大小，单位字节")
        Long sizeBytes,

        @Schema(description = "受权限保护的图片访问地址")
        String url,

        @Schema(description = "上传时间")
        LocalDateTime createdAt
) {
}
