package com.agentdoc.common.feign.vo;

import java.time.Instant;
import java.util.List;

/**
 * 批量 Replay 创建结果以及仅绑定本批 Task 集合的 WorkerCapability。
 * <p>
 * 批量新建回放任务后的顶层返回对象，包含每条任务创建明细，
 * 同时一次性下发适配这批任务的窄权限令牌，供Worker直接拉起回放计算。
 *
 * @param runId                      评估运行实例ID
 * @param spaceId                    工作空间ID，权限隔离边界
 * @param items                      本批次回放任务创建明细列表
 * @param taskIdsHash                本批任务ID集合哈希，用于校验任务范围不可篡改
 * @param workerCapability           为本批任务签发的Worker窄权限JWT令牌
 * @param workerCapabilityExpiresAt  令牌过期时间戳（Instant）
 */
public record ReplayBatchCreateVO(
        Long runId,
        Long spaceId,
        List<ReplayBatchItemVO> items,
        String taskIdsHash,
        String workerCapability,
        Instant workerCapabilityExpiresAt) {
}
