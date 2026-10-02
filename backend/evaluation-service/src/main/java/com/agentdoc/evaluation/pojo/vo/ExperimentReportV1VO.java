package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 报告 schema v1 的公开嵌套类型；仅用于读取，不参与历史报告重算或 hash。 */
public final class ExperimentReportV1VO {
    private ExperimentReportV1VO() { }

    @Schema(description = "报告 v1 SelectedRecordIds")
    public record SelectedRecordIdsVO(
            @Schema(description = "运行 ID 集合") List<Long> runIds,
            @Schema(description = "执行尝试 ID 集合") List<Long> attemptIds,
            @Schema(description = "评价结果 ID 集合") List<Long> resultIds,
            @Schema(description = "指标 ID 集合") List<Long> metricIds,
            @Schema(description = "证据 ID 集合") List<Long> evidenceIds,
            @Schema(description = "反馈 ID 集合") List<Long> feedbackIds
    ) { }

    @Schema(description = "报告 v1 Content")
    public record ContentVO(
            @Schema(description = "冻结预期用例数") int expectedCaseCount,
            @Schema(description = "变体数") int variantCount,
            @Schema(description = "用例执行选择") List<CaseSelectionVO> cases,
            @Schema(description = "完整指标单元") List<CellVO> cells,
            @Schema(description = "变体汇总") List<VariantSummaryVO> summaries,
            @Schema(description = "配对比较") List<PairComparisonVO> comparisons,
            @Schema(description = "指标证据") List<MetricEvidenceVO> metricEvidence,
            @Schema(description = "人工反馈") List<FeedbackVO> feedback,
            @Schema(description = "反馈覆盖") List<FeedbackCoverageVO> feedbackCoverage,
            @Schema(description = "授权 Token") Long authorizedTokenBudget,
            @Schema(description = "实际 Token，未知为空") Long actualTokenUsage,
            @Schema(description = "Token 超额，未知为空") Long budgetOverrun
    ) { }

    @Schema(description = "报告 v1 CaseSelection")
    public record CaseSelectionVO(
            @Schema(description = "变体标识") String variantKey,
            @Schema(description = "运行 ID") Long runId,
            @Schema(description = "用例版本 ID") Long testCaseVersionId,
            @Schema(description = "用例运行 ID") Long caseRunId,
            @Schema(description = "执行尝试 ID") Long attemptId,
            @Schema(description = "Task ID") Long taskId,
            @Schema(description = "尝试状态") String attemptStatus,
            @Schema(description = "失败码") String failureCode
    ) { }

    @Schema(description = "报告 v1 Cell")
    public record CellVO(
            @Schema(description = "指标值及缺失原因") MetricValueVO value,
            @Schema(description = "关联证据 ID") List<Long> evidenceIds
    ) { }

    @Schema(description = "报告 v1 MetricValue")
    public record MetricValueVO(
            @Schema(description = "用例版本 ID") Long testCaseVersionId,
            @Schema(description = "指标标识") String metricKey,
            @Schema(description = "评估器版本 ID，可空") Long evaluatorVersionId,
            @Schema(description = "变体标识") String variantKey,
            @Schema(description = "运行 ID") Long runId,
            @Schema(description = "指标记录 ID，可空") Long metricId,
            @Schema(description = "数值，保留十进制精度") BigDecimal numericValue,
            @Schema(description = "布尔值，false 不代表缺失") Boolean booleanValue,
            @Schema(description = "字符串原值") String stringValue,
            @Schema(description = "单位或币种") String unit,
            @Schema(description = "缺失原因，可空") String missingReason
    ) { }

    @Schema(description = "报告 v1 VariantSummary")
    public record VariantSummaryVO(
            @Schema(description = "变体标识") String variantKey,
            @Schema(description = "指标标识") String metricKey,
            @Schema(description = "冻结分母") long expectedCount,
            @Schema(description = "有效数") long validCount,
            @Schema(description = "缺失数") long missingCount,
            @Schema(description = "真值数") long trueCount,
            @Schema(description = "假值数") long falseCount,
            @Schema(description = "按单位合计") Map<String, BigDecimal> numericTotalsByUnit,
            @Schema(description = "按单位均值") Map<String, BigDecimal> numericMeansByUnit,
            @Schema(description = "按原因计数") Map<String, Long> missingReasons
    ) { }

    @Schema(description = "报告 v1 PairComparison")
    public record PairComparisonVO(
            @Schema(description = "候选标识") String candidateVariantKey,
            @Schema(description = "指标标识") String metricKey,
            @Schema(description = "可配对数") long pairedCount,
            @Schema(description = "币种不可比数") long incomparableCurrencyCount,
            @Schema(description = "改善数") long improvedCount,
            @Schema(description = "劣化数") long worsenedCount,
            @Schema(description = "不变数") long unchangedCount,
            @Schema(description = "候选减基线均值，可空") BigDecimal meanCandidateMinusBaseline,
            @Schema(description = "按币种的配对差值均值") Map<String, BigDecimal> meanCandidateMinusBaselineByCurrency
    ) { }

    @Schema(description = "报告 v1 MetricEvidence")
    public record MetricEvidenceVO(
            @Schema(description = "指标 ID") Long metricId,
            @Schema(description = "证据 ID") Long evidenceId,
            @Schema(description = "证据类型") String evidenceType,
            @Schema(description = "业务引用") String businessId,
            @Schema(description = "内容 hash") String contentHash
    ) { }

    @Schema(description = "报告 v1 Feedback")
    public record FeedbackVO(
            @Schema(description = "反馈 ID") Long id,
            @Schema(description = "变体标识") String variantKey,
            @Schema(description = "用例版本 ID") Long testCaseVersionId,
            @Schema(description = "来源类型") String sourceType,
            @Schema(description = "来源业务引用") String sourceBusinessId,
            @Schema(description = "反馈标签") String label,
            @Schema(description = "反馈分值，可空") BigDecimal score
    ) { }

    @Schema(description = "报告 v1 FeedbackCoverage")
    public record FeedbackCoverageVO(
            @Schema(description = "变体标识") String variantKey,
            @Schema(description = "来源类型") String sourceType,
            @Schema(description = "已覆盖用例数") long coveredCaseCount
    ) { }

}
