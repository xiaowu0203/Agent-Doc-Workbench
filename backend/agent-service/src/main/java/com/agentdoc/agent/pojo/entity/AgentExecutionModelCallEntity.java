package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 单轮模型调用脱敏审计实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_execution_model_call")
@Schema(description = "Agent 单轮模型调用脱敏审计实体")
public class AgentExecutionModelCallEntity extends BaseEntity {
    @Schema(description = "Agent 执行 ID")
    private Long executionId;
    @Schema(description = "执行内模型调用序号")
    private Integer sequenceNo;
    @Schema(description = "实际模型 ID")
    private Long modelId;
    @Schema(description = "实际模型配置版本")
    private Long modelConfigVersion;
    @Schema(description = "实际模型标识")
    private String modelKey;
    @Schema(description = "本轮最大输出 Token")
    private Integer maxOutputTokens;
    @Schema(description = "本轮温度参数")
    private Double temperature;
    @Schema(description = "是否流式调用")
    private Boolean streaming;
    @Schema(description = "规范化消息 SHA-256")
    private String messagesSha256;
    @Schema(description = "规范化消息 UTF-8 字节数")
    private Long messagesSize;
    @Schema(description = "规范化响应 SHA-256")
    private String responseSha256;
    @Schema(description = "规范化响应 UTF-8 字节数")
    private Long responseSize;
    @Schema(description = "审计状态")
    private String status;
    @Schema(description = "异常类型")
    private String errorType;
    @Schema(description = "调用开始时间")
    private LocalDateTime startedAt;
    @Schema(description = "调用结束时间")
    private LocalDateTime finishedAt;
}
