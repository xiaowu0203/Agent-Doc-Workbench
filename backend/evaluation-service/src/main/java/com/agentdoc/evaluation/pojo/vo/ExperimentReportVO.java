package com.agentdoc.evaluation.pojo.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 不可变实验报告版本及其冻结计算输入。 */
public record ExperimentReportVO(Long experimentId, Integer revision, Integer schemaVersion,
                                 String manifestHash, String calculationInputHash,
                                 JsonNode selectedRecordIds, JsonNode report,
                                 String contentHash, Long generatedBy, LocalDateTime generatedAt) { }
