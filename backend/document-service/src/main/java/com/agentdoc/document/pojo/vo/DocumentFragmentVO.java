package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档片段视图对象（按偏移读取，供 MCP Agent 按需加载控 Token）。
 */
@Schema(description = "文档片段")
public record DocumentFragmentVO(

        @Schema(description = "文档 ID")
        Long documentId,

        @Schema(description = "片段内容")
        String content,

        @Schema(description = "起始偏移（字符数，从 0 起）")
        long start,

        @Schema(description = "实际返回长度（字符数）")
        int length,

        @Schema(description = "文档总长度（字符数）")
        long totalLength
) {
}
