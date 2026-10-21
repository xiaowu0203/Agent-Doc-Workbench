package com.agentdoc.common.feign.dto;

/**
 * 独立工具明细分页，不受执行记录页的任务集合限制。
 * @param query 空间、工具调用时间范围和筛选条件
 * @param pageNum 页码
 * @param pageSize 每页条数，最大100
 */
public record AgentToolCallSearchDTO(AgentToolUsageQueryDTO query, Integer pageNum, Integer pageSize) { }
