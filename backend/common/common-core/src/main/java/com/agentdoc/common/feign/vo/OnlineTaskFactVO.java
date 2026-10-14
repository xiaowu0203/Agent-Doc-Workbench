package com.agentdoc.common.feign.vo;
/** 单次权威观察；未知不能被补为零。
 * @param taskId 任务 @param bindingHash 绑定 @param taskStatus Task事实
 * @param executionId 执行 @param executionStatus Agent实际终态 @param ledgerTokens 账本
 * @param neverDispatched 权威未投递 @param cancelRequested 取消请求 @param executionFinishedAt Agent实际终态时间 */
public record OnlineTaskFactVO(String taskId, String bindingHash, String taskStatus,
        String executionId, String executionStatus, String ledgerTokens,
        boolean neverDispatched, boolean cancelRequested, String executionFinishedAt) { }
