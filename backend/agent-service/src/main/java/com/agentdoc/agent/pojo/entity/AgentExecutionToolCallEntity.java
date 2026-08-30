package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_execution_tool_call")
@Schema(description = "Agent 工具调用脱敏审计实体")
public class AgentExecutionToolCallEntity extends BaseEntity {
    @Schema(description = "Agent 执行 ID")
    private Long executionId;
    @Schema(description = "执行内调用序号")
    private Integer sequenceNo;
    @Schema(description = "模型可见工具名")
    private String toolName;
    @Schema(description = "工具来源")
    private String toolSource;
    @Schema(description = "工具来源标识")
    private String toolSourceKey;
    @Schema(description = "外部 MCP Server ID")
    private Long mcpServerId;
    @Schema(description = "参数 SHA-256")
    private String argumentsSha256;
    @Schema(description = "参数 UTF-8 字节数")
    private Long argumentsSize;
    @Schema(description = "结果 SHA-256")
    private String resultSha256;
    @Schema(description = "结果 UTF-8 字节数")
    private Long resultSize;
    @Schema(description = "审计状态")
    private String status;
    @Schema(description = "异常类型")
    private String errorType;
    @Schema(description = "调用开始时间")
    private LocalDateTime startedAt;
    @Schema(description = "调用结束时间")
    private LocalDateTime finishedAt;
}
