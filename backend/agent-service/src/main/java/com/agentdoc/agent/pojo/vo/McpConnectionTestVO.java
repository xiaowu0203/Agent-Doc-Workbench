package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.McpConnectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** MCP Server 连接测试与工具发现结果。 */
@Schema(description = "MCP Server 连接测试与工具发现结果")
public record McpConnectionTestVO(
        @Schema(description = "MCP Server ID") Long serverId,
        @Schema(description = "是否连接成功") boolean connected,
        @Schema(description = "测试状态") McpConnectionStatus status,
        @Schema(description = "测试完成时间") LocalDateTime testedAt,
        @Schema(description = "握手与工具发现总耗时，单位毫秒") Long durationMs,
        @Schema(description = "失败错误摘要；成功时为空") String errorMessage,
        @Schema(description = "本次成功发现的工具；失败时为空数组") List<McpToolVO> tools) {

    public McpConnectionTestVO {
        tools = List.copyOf(tools);
    }
}
