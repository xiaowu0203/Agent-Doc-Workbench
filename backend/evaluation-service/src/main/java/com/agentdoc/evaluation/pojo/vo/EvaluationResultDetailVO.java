package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.common.feign.vo.MetricEvidenceReferenceVO;
import com.agentdoc.common.feign.vo.StandardMetricVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "不可变评估结果详情；Metric 是跨阶段比较契约，detailsJson 仅用于诊断")
public record EvaluationResultDetailVO(
        @Schema(description = "评估结果详情ID")
        Long id,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "评估运行ID")
        Long runId,

        @Schema(description = "用例尝试ID")
        Long caseAttemptId,

        @Schema(description = "评估器版本ID")
        Long evaluatorVersionId,

        @Schema(description = "评估器重试序号")
        Integer evaluationAttemptNo,

        @Schema(description = "评估执行状态")
        String status,

        @Schema(description = "评估得分")
        BigDecimal score,

        @Schema(description = "结果摘要编码")
        String summaryCode,

        @Schema(description = "诊断详情JSON，仅供排查，不参与指标比对")
        String detailsJson,

        @Schema(description = "评估器实现版本")
        String implementationVersion,

        @Schema(description = "链路追踪 TraceId")
        String traceId,

        @Schema(description = "链路追踪 SpanId")
        String spanId,

        @Schema(description = "评估开始时间")
        LocalDateTime startedAt,

        @Schema(description = "评估结束时间")
        LocalDateTime finishedAt,

        @Schema(description = "记录创建时间")
        LocalDateTime createdAt,

        @Schema(description = "标准化指标集合，跨阶段比对契约")
        List<StandardMetricVO> metrics,

        @Schema(description = "指标证据引用集合")
        List<MetricEvidenceReferenceVO> evidence,

        @Schema(description = "关联人工反馈集合")
        List<EvaluationFeedbackVO> feedback
) {}