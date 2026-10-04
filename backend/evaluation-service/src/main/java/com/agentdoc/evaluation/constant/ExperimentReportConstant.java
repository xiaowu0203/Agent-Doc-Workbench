package com.agentdoc.evaluation.constant;

/** 报告读取契约与完整性告警的领域约束。 */
public final class ExperimentReportConstant {
    public static final int V1_SCHEMA = 1;
    public static final long INTEGRITY_ALERT_SECONDS = 3600;
    public static final String INTEGRITY_ALERT_PREFIX = "experiment:report:integrity:";
    private ExperimentReportConstant() { }
}
