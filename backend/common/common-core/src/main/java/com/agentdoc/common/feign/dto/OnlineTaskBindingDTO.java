package com.agentdoc.common.feign.dto;

/** 不含正文/凭证的不可变线上业务身份；全部 ID/Long 进入规范文本。
 * @param assignmentId 分配身份 @param experimentId 实验身份 @param manifestHash 清单摘要
 * @param acceptedSequence 接受顺序 @param variant 固定组 @param bucket 固定桶
 * @param actorId 发起人 @param taskId 原 Task @param spaceId 空间 @param agentId Agent
 * @param documentId 文档 @param documentVersion 冻结版本 @param documentContentHash 冻结内容
 * @param inputSchemaVersion 输入协议 @param inputHash 输入摘要 @param templateId 模板
 * @param templateSchemaVersion 模板协议 @param templateHash 模板摘要 @param nonPromptHash 非 Prompt 证明
 * @param dependencyHash 依赖证明 @param tokenBudget 有限预算 @param executionTimeoutSeconds 超时
 * @param executionMode LIVE @param lineageType ORIGINAL @param schemaVersion 绑定协议 */
public record OnlineTaskBindingDTO(String assignmentId, String experimentId, String manifestHash,
        String acceptedSequence, String variant, int bucket, String actorId, String taskId, String spaceId,
        String agentId, String documentId, String documentVersion, String documentContentHash,
        int inputSchemaVersion, String inputHash, String templateId, int templateSchemaVersion,
        String templateHash, String nonPromptHash, String dependencyHash, String tokenBudget,
        int executionTimeoutSeconds, String executionMode, String lineageType, int schemaVersion) { }
