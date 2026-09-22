package com.agentdoc.common.feign.vo;

/**
 * 批量 Replay 的稳定请求映射。
 * <p>
 * 批量创建回放时单条映射记录，建立原始任务与新建回放任务之间的关联，
 * 通过 derivationRequestKey 实现幂等，避免重复创建回放任务。
 *
 * @param sourceTaskId          原始来源工作台任务ID
 * @param derivationRequestKey  派生请求唯一标识，用于幂等去重
 * @param replayTaskId          新建的评估回放任务ID
 * @param taskStatus            回放任务初始状态
 */
public record ReplayBatchItemVO(
        Long sourceTaskId,
        String derivationRequestKey,
        Long replayTaskId,
        String taskStatus) {
}
