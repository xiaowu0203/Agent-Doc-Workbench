package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * WorkerCapability 约束下的 Evaluation Task 批量查询。
 * <p>
 * 由Evaluation Worker携带窄权限令牌发起，查询范围受令牌内runId、spaceId、taskIdsHash约束，
 * 只能查询令牌允许的评估任务集合，越界查询将被拒绝。
 *
 * @param runId     评估运行实例ID
 * @param spaceId   工作空间ID，权限隔离边界
 * @param taskIds   需要批量查询的评估任务ID列表
 */
public record EvaluationTaskBatchQueryDTO(Long runId, Long spaceId, List<Long> taskIds) {
}
