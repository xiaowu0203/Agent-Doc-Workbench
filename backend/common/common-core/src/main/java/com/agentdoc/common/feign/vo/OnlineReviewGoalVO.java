package com.agentdoc.common.feign.vo;

/** 冻结业务目标，与Agent系统Prompt分开。
 * @param taskId 原Task @param inputSnapshotHash 整体业务输入摘要 @param instruction 冻结用户指令 */
public record OnlineReviewGoalVO(String taskId, String inputSnapshotHash, String instruction) { }
