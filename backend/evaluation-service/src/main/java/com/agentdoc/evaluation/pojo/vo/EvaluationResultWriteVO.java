package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "评估结果原子写入响应")
public record EvaluationResultWriteVO(
        @Schema(description = "评估结果主键ID")
        Long resultId,

        @Schema(description = "评估结果最终状态")
        String status,

        @Schema(description = "本次写入新增指标ID列表")
        List<Long> metricIds,

        @Schema(description = "本次写入新增证据引用ID列表")
        List<Long> evidenceReferenceIds
) {}