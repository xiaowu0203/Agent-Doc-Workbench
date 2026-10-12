package com.agentdoc.common.feign.vo;


/** 不包含正文、Prompt和Trace的Task/权威账本事实。
 * @param taskId Task @param assignmentId 分配 @param bindingHash 冻结绑定摘要
 * @param state 查询状态 @param status Task状态 @param executionId 执行身份
 * @param createdAt Task创建时间 @param startedAt Task开始时间 @param finishedAt Task终态时间
 * @param ledgerId 权威账本身份 @param ledgerHash 权威账本脱敏投影摘要
 * @param ledgerRecordedAt 账本事实记录时间 @param usage 冻结计价与用量 */
public record OnlineReportTaskFactVO(String taskId, String assignmentId, String bindingHash, String state,
        String status, String executionId, String createdAt, String startedAt, String finishedAt,
        String ledgerId, String ledgerHash, String ledgerRecordedAt, OnlineReportUsageVO usage) { }
