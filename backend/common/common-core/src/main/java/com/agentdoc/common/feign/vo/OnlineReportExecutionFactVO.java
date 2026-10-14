package com.agentdoc.common.feign.vo;


/** 独立Agent执行事实，不将Task终态视为执行终态。
 * @param taskId Task @param assignmentId 分配 @param bindingHash 冻结绑定摘要
 * @param state 查询状态 @param executionId 执行身份 @param status 执行状态
 * @param createdAt 创建 @param startedAt 实际开始 @param finishedAt 实际终态 @param usage 冻结价格与用量
 * @param originalTextCapturedAt 原始文本捕获时间，不证明当前正文仍可访问
 * @param originalTextIdentityHash 原始文本捕获身份摘要，不包含正文 */
public record OnlineReportExecutionFactVO(String taskId, String assignmentId, String bindingHash, String state,
        String executionId, String status, String createdAt, String startedAt, String finishedAt,
        OnlineReportUsageVO usage, String originalTextCapturedAt, String originalTextIdentityHash) { }
