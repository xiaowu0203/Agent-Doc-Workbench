package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;

import java.time.LocalDateTime;

public record TestCaseVersionVO(
        Long id, Long testCaseId, Long spaceId, Integer versionNo, String status,
        Long sourceTaskId, Long sourceExecutionId, Integer sourceInputSchemaVersion, String sourceInputHash,
        Integer sourceExecutionSchemaVersion, String sourceExecutionHash, Long documentVersionSnapshot,
        String documentContentSha256, Integer expectedSchemaVersion, String expectedJson, String sourceType,
        String sanitizationNote, String contentHash, LocalDateTime publishedAt, Long createdBy) {
    public static TestCaseVersionVO from(EvaluationTestCaseVersionEntity value) {
        return new TestCaseVersionVO(value.getId(), value.getTestCaseId(), value.getSpaceId(), value.getVersionNo(),
                value.getStatus(), value.getSourceTaskId(), value.getSourceExecutionId(),
                value.getSourceInputSchemaVersion(), value.getSourceInputHash(),
                value.getSourceExecutionSchemaVersion(), value.getSourceExecutionHash(),
                value.getDocumentVersionSnapshot(), value.getDocumentContentSha256(),
                value.getExpectedSchemaVersion(), value.getExpectedJson(), value.getSourceType(),
                value.getSanitizationNote(), value.getContentHash(), value.getPublishedAt(), value.getCreatedBy());
    }
}
