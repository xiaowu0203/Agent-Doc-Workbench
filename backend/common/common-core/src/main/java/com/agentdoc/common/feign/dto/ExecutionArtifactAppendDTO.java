package com.agentdoc.common.feign.dto;

/** Agent Runtime 向 task-service 追加隔离执行产物的内部契约。 */
public record ExecutionArtifactAppendDTO(
        Long executionId,
        Long sourceTaskId,
        Integer sequenceNo,
        Long sourceToolCallId,
        String artifactType,
        Integer schemaVersion,
        String payloadJson,
        String payloadSha256) {
}
