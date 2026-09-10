package com.agentdoc.common.feign.vo;

/**
 * 工具来源调用次数。
 *
 * @param source 工具来源分类
 * @param calls 调用次数
 */
public record AgentToolSourceCountVO(String source, long calls) {
}
