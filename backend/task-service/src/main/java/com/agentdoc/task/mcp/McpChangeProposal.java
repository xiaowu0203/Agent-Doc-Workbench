package com.agentdoc.task.mcp;

import com.agentdoc.common.feign.dto.ChangeItemDTO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record McpChangeProposal(
        Long baseVersion,
        @ArraySchema(schema = @Schema(implementation = ChangeItemSchema.class))
        List<ChangeItemDTO> changes,
        String summary) {

    /** 仅用于 MCP schema；生成器未遵循枚举 @JsonValue，实际载荷仍由 ChangeItemDTO 解析。 */
    public record ChangeItemSchema(
            @Schema(description = "变更操作编码", allowableValues = {"replace", "append"}) String op,
            @Schema(description = "替换时用于核对的原文，追加时为空文本") String oldText,
            @Schema(description = "替换后的全文或追加内容") String newText) { }
}
