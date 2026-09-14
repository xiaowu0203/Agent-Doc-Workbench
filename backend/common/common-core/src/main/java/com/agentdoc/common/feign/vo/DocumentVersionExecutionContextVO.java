package com.agentdoc.common.feign.vo;

/**
 * 已按版本与摘要校验的文档执行上下文。
 */
public record DocumentVersionExecutionContextVO(Long documentId, Long version,
                                                String contentSha256, Long contentLength) {
}
