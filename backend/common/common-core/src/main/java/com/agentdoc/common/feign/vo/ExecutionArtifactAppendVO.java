package com.agentdoc.common.feign.vo;

/** 隔离执行产物追加结果。 */
public record ExecutionArtifactAppendVO(
        Long artifactId,
        String artifactType,
        String payloadSha256) {
}
