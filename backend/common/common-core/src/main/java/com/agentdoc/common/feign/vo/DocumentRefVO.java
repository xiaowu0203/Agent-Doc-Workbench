package com.agentdoc.common.feign.vo;

/**
 * 文档引用投影（Feign 契约：文档服务返回的最小字段，用于审批队列标题回填等）。
 *
 * @param id      文档 ID
 * @param spaceId 所属空间 ID
 * @param title   文档标题
 */
public record DocumentRefVO(Long id, Long spaceId, String title) {
}
