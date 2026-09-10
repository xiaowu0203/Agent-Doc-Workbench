package com.agentdoc.common.feign.vo;

/**
 * 任务执行前由 document-service 返回的文档上下文。
 */
public record DocumentExecutionContextVO(Long documentId, Long spaceId, Integer docType,
                                         Integer status, Long version, Long contentLength) {

    /**
     * 文档是否处于可创建任务的正常状态。
     */
    public boolean normal() {
        return Integer.valueOf(1).equals(status);
    }
}
