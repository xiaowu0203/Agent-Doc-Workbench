package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 不可变 Prompt 候选配置的非敏感身份投影。
 *
 * @param candidateConfigId 候选配置 ID
 * @param spaceId 所属空间 ID
 * @param agentId 来源 Agent ID
 * @param sourceTaskId 来源 Task ID
 * @param sourceExecutionId 来源 AgentExecution ID
 * @param sourceSnapshotSchemaVersion 来源执行快照 schema 版本
 * @param sourceSnapshotHash 来源执行快照 hash
 * @param candidateSnapshotSchemaVersion 候选执行快照 schema 版本
 * @param candidateSnapshotHash 候选执行快照 hash
 * @param snapshotWithoutPromptHash 移除 systemPrompt 后的公共快照 hash
 * @param promptDiffFieldPaths 实际 Prompt 差异字段路径
 */
public record AgentCandidateConfigVO(
        Long candidateConfigId,
        Long spaceId,
        Long agentId,
        Long sourceTaskId,
        Long sourceExecutionId,
        Integer sourceSnapshotSchemaVersion,
        String sourceSnapshotHash,
        Integer candidateSnapshotSchemaVersion,
        String candidateSnapshotHash,
        String snapshotWithoutPromptHash,
        List<String> promptDiffFieldPaths) {

    public AgentCandidateConfigVO {
        promptDiffFieldPaths = List.copyOf(promptDiffFieldPaths);
    }
}
