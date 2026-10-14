package com.agentdoc.common.feign.vo;

/**
 * 当前人类授权后的线上冻结输入投影；不含正文与分组。
 * @param taskId 任务身份
 * @param executionId 实际执行身份
 * @param spaceId 当前空间
 * @param agentId 执行 Agent
 * @param experimentId 实验身份
 * @param assignmentId 分配身份
 * @param bindingHash 分配绑定摘要
 * @param documentId 原文档身份
 * @param documentType 冻结文档类型
 * @param baseVersion 冻结基线版本
 * @param inputHash 冻结输入正文摘要
 * @param completed 是否为已完成的 LIVE 任务
 */
public record OnlineOriginalInputVO(String taskId, String executionId, String spaceId, String agentId,
        String experimentId, String assignmentId, String bindingHash, String documentId,
        String documentType, Long baseVersion, String inputHash, boolean completed) { }
