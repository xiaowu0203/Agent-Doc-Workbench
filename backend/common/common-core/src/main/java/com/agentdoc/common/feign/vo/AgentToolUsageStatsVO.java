package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * Agent 工具调用聚合统计。
 *
 * @param totalCalls 总调用次数
 * @param sources 各来源调用次数
 */
public record AgentToolUsageStatsVO(long totalCalls, List<AgentToolSourceCountVO> sources) {
}
