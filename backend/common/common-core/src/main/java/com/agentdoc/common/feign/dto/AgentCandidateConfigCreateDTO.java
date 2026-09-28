package com.agentdoc.common.feign.dto;

/**
 * 创建不可变 Prompt 候选配置的内部请求。
 *
 * @param requestKey 创建幂等键
 * @param spaceId 所属空间 ID
 * @param sourceTaskId 来源 Task ID
 * @param sourceExecutionId 来源 AgentExecution ID
 * @param sourceSnapshotSchemaVersion 来源执行快照 schema 版本
 * @param sourceSnapshotHash 来源执行快照 hash
 * @param agentPrompt 候选 Agent Prompt 正文
 */
public record AgentCandidateConfigCreateDTO(
        String requestKey,
        Long spaceId,
        Long sourceTaskId,
        Long sourceExecutionId,
        Integer sourceSnapshotSchemaVersion,
        String sourceSnapshotHash,
        String agentPrompt) {
}
