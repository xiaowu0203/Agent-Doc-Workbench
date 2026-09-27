package com.agentdoc.evaluation.pojo.vo;

import java.time.LocalDateTime;

/** 不含报告正文的不可变版本索引。 */
public record ExperimentReportRevisionVO(Long experimentId, Integer revision, Integer schemaVersion,
                                         String contentHash, Long generatedBy, LocalDateTime generatedAt) { }
