package com.agentdoc.evaluation.enums;

/** 固定的报告读取兼容状态，不改变报告持久化身份。 */
public enum ExperimentReportCompatibility {
    SUPPORTED, SCHEMA_MISSING, SCHEMA_INVALID, SCHEMA_UNSUPPORTED,
    PAYLOAD_INVALID, CONTENT_HASH_MISMATCH
}
