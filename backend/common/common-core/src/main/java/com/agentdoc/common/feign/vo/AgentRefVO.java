package com.agentdoc.common.feign.vo;

/**
 * Agent 展示用最小引用投影。
 *
 * @param id Agent ID
 * @param spaceId 所属空间 ID
 * @param name Agent 名称
 */
public record AgentRefVO(Long id, Long spaceId, String name) {
}
