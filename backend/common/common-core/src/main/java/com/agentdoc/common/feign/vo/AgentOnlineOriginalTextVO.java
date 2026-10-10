package com.agentdoc.common.feign.vo;

/**
 * 当前资源授权后读取的线上最终文本。正文只用于受控证据链路，不进入普通结果或日志。
 * @param schemaVersion 原始证据协议版本
 * @param state AVAILABLE/EVIDENCE_UNAVAILABLE
 * @param evidenceId Agent 原始文本身份
 * @param taskId 原 Task 身份
 * @param executionId 实际 Execution 身份
 * @param spaceId 原所属空间
 * @param agentId 原 Agent 身份
 * @param experimentId 原实验身份
 * @param assignmentId 原分配身份
 * @param bindingHash 冻结分配摘要
 * @param contentHash 原始正文 SHA-256
 * @param identityHash 身份、内容摘要与捕获时间的协议摘要
 * @param capturedAt 原始捕获时间，ISO 本地时间，毫秒精度
 * @param originalText 完整最终文本，不用历史摘要补齐
 */
public record AgentOnlineOriginalTextVO(int schemaVersion, String state, String evidenceId,
        String taskId, String executionId, String spaceId, String agentId, String experimentId,
        String assignmentId, String bindingHash, String contentHash, String identityHash,
        String capturedAt, String originalText) { }
