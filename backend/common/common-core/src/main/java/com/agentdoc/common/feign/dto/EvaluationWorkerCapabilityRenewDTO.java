package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 用户显式操作时，为既有 Evaluation Replay Task 重新签发 WorkerCapability。
 * <p>
 * 用于评估回放场景下刷新窄权限令牌，沿用原有评估运行与任务上下文，
 * 重新生成有效期更新的能力令牌，权限范围由传入的任务ID列表决定。
 *
 * @param runId        评估运行实例ID
 * @param spaceId      工作空间ID，权限隔离边界
 * @param ttlSeconds   新令牌的有效期，单位秒
 * @param taskIds      本次令牌绑定的评估任务ID集合
 */
public record EvaluationWorkerCapabilityRenewDTO(
        Long runId,
        Long spaceId,
        Long ttlSeconds,
        List<Long> taskIds) {
}
