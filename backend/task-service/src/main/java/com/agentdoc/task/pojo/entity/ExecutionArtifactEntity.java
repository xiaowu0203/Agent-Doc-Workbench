package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("execution_artifact")
@Schema(description = "隔离执行不可变候选产物")
public class ExecutionArtifactEntity extends BaseEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;
    @Schema(description = "隔离任务 ID")
    private Long taskId;
    @Schema(description = "AgentExecution ID")
    private Long executionId;
    @Schema(description = "Replay 来源任务 ID")
    private Long sourceTaskId;
    @Schema(description = "执行内稳定序号")
    private Integer sequenceNo;
    @Schema(description = "来源工具调用审计 ID")
    private Long sourceToolCallId;
    @Schema(description = "产物类型")
    private String artifactType;
    @Schema(description = "payload schema 版本")
    private Integer schemaVersion;
    @Schema(description = "结构化候选内容")
    private String payloadJson;
    @Schema(description = "payload 稳定 SHA-256")
    private String payloadSha256;
}
