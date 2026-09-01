package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 保存文档未提交草稿请求。
 */
@Schema(description = "保存文档未提交草稿请求")
public record DocumentDraftSaveDTO(

        @Schema(description = "草稿基于的文档版本号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "草稿基线版本不能为空")
        Long baseVersion,

        @Schema(description = "草稿标题")
        @Size(max = 200, message = "草稿标题最长 200 字符")
        String title,

        @Schema(description = "Markdown 草稿内容", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "草稿内容不能为空")
        String content
) {
}
