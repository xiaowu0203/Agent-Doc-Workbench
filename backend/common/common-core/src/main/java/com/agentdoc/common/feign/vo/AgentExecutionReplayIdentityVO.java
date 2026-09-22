package com.agentdoc.common.feign.vo;

/**
 * Replay 回放准入所需的最小 AgentExecution 身份投影对象
 * <p>仅携带回放校验必须的核心字段，不暴露完整执行快照数据，减少敏感信息泄露。</p>
 * @param executionCount 执行序号，标记当前是第几次执行
 * @param executionId Agent执行记录唯一ID
 * @param executionSnapshotSchemaVersion 执行快照协议版本号
 * @param executionSnapshotHash 执行快照规范化哈希值，用于快照完整性校验
 * @param snapshotValid 快照有效性标记：true代表快照完整可用
 * @param externalMcpPresent 是否存在外部MCP能力依赖
 */
public record AgentExecutionReplayIdentityVO(
        int executionCount,
        Long executionId,
        Integer executionSnapshotSchemaVersion,
        String executionSnapshotHash,
        boolean snapshotValid,
        boolean externalMcpPresent) {
}