package com.agentdoc.common.feign.dto;

/**
 * 文档回滚审计写入请求。
 *
 * @param spaceId 所属空间 ID
 * @param documentId 文档 ID
 * @param versionId 新生成的版本记录 ID
 * @param versionNo 新生成的版本号
 * @param rollbackFromVersion 回滚来源版本号
 */
public record DocumentVersionRollbackAuditDTO(
        Long spaceId,
        Long documentId,
        Long versionId,
        Long versionNo,
        Long rollbackFromVersion) {
}

