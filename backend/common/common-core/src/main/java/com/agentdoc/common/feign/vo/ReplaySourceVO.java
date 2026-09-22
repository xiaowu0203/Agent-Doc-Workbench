package com.agentdoc.common.feign.vo;

/**
 * 可用于评估用例冻结的 Replay 来源投影。
 * <p>
 * 提取原始任务可回放所需的全部基线上下文，用于校验是否满足冻结评估用例条件；
 * 携带多组快照哈希与版本号，保证回放链路可复现、上下文不可篡改。
 *
 * @param replayable                     是否支持回放
 * @param reasonCode                     不可回放原因码，可回放时为null
 * @param sourceTaskId                   来源原始任务ID
 * @param sourceExecutionId              来源Agent执行记录ID
 * @param spaceId                        工作空间ID，权限隔离边界
 * @param rootTaskId                     顶层根任务ID，用于溯源链路归属
 * @param sourceLineage                  来源任务血缘标识，可用于追踪链式执行
 * @param replayDepth                    当前回放嵌套深度，防止无限递归回放
 * @param inputSnapshotSchemaVersion     输入快照Schema版本，序列化兼容
 * @param inputSnapshotHash              输入上下文快照哈希
 * @param executionSnapshotSchemaVersion 执行快照Schema版本，序列化兼容
 * @param executionSnapshotHash          完整执行上下文快照哈希
 * @param documentVersionSnapshot        基线文档冻结版本号
 * @param documentContentSha256          基线文档内容SHA256哈希
 */
public record ReplaySourceVO(
        boolean replayable,
        String reasonCode,
        Long sourceTaskId,
        Long sourceExecutionId,
        Long spaceId,
        Long rootTaskId,
        String sourceLineage,
        Integer replayDepth,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        Integer executionSnapshotSchemaVersion,
        String executionSnapshotHash,
        Long documentVersionSnapshot,
        String documentContentSha256) {
}
