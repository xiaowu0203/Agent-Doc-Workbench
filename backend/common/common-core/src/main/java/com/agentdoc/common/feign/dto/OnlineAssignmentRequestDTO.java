package com.agentdoc.common.feign.dto;

/** Task 意图已冻结后申请一次分配。
 * @param taskId 预分配Task @param spaceId 空间 @param agentId Agent @param documentId 文档
 * @param actorId 原发起人 @param requestKey 幂等键 @param requestHash 原请求摘要
 * @param inputHash 冻结输入摘要 @param documentVersion 版本 @param documentContentHash 正文摘要
 * @param tokenBudget 实际有限预算 @param inputSchemaVersion 输入协议 */
public record OnlineAssignmentRequestDTO(String taskId, String spaceId, String agentId, String documentId,
        String actorId, String requestKey, String requestHash, String inputHash, String documentVersion,
        String documentContentHash, String tokenBudget, int inputSchemaVersion) { }
