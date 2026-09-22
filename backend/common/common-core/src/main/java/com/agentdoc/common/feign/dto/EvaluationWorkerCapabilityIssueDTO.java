package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 签发 Evaluation Worker 窄权限令牌的内部契约。
 *
 * @param runId        评估执行实例ID，绑定本次评估运行上下文
 * @param spaceId      空间ID，令牌权限限定在该工作空间内
 * @param taskIdsHash  评估覆盖的任务ID集合的哈希值，防止任务范围被篡改
 * @param ttlSeconds   令牌有效期（秒），控制窄令牌最长存活时间
 * @param actions      允许的操作权限集合，如评估读取、指标上报等细粒度动作
 */
public record EvaluationWorkerCapabilityIssueDTO(
        Long runId,
        Long spaceId,
        String taskIdsHash,
        Long ttlSeconds,
        List<String> actions) {
}
