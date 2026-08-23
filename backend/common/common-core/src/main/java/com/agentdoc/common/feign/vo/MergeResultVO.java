package com.agentdoc.common.feign.vo;

/**
 * 文档合并结果。
 * @param documentId 文档 ID
 * @param title      文档标题
 * @param newVersion 合并后的新版本号
 */
public record MergeResultVO(Long documentId, String title, Long newVersion) {
}
