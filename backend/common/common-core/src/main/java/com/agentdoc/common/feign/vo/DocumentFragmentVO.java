package com.agentdoc.common.feign.vo;

/**
 * 文档片段跨服务传输对象。
 */
public record DocumentFragmentVO(Long documentId, String content, long start,
                                 int length, long totalLength) {
}
