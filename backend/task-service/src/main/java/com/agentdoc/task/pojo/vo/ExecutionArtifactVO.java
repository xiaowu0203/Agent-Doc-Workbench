package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.pojo.entity.ExecutionArtifactEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "隔离执行不可变候选产物")
public record ExecutionArtifactVO(
        Long id,
        Long taskId,
        Long executionId,
        Long sourceTaskId,
        Integer sequenceNo,
        Long sourceToolCallId,
        String artifactType,
        Integer schemaVersion,
        String payloadJson,
        String payloadSha256,
        LocalDateTime createdAt) {

    public static ExecutionArtifactVO from(ExecutionArtifactEntity entity) {
        return new ExecutionArtifactVO(entity.getId(), entity.getTaskId(), entity.getExecutionId(),
                entity.getSourceTaskId(), entity.getSequenceNo(), entity.getSourceToolCallId(),
                entity.getArtifactType(), entity.getSchemaVersion(), entity.getPayloadJson(),
                entity.getPayloadSha256(), entity.getCreatedAt());
    }
}
