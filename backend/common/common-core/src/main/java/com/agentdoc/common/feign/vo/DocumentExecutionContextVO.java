package com.agentdoc.common.feign.vo;

/**
 * 任务执行前由 document-service 返回的文档上下文。
 */
public record DocumentExecutionContextVO(Long documentId, Long spaceId, Integer docType,
                                         Integer status, Long version) {
}
