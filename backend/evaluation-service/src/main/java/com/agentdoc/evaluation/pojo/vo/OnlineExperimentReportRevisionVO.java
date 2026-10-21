package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 不读取报告正文和冻结输入的分页版本索引。 */
public record OnlineExperimentReportRevisionVO(
        @Schema(description = "线上实验ID，规范十进制文本") String experimentId,
        @Schema(description = "不可变报告版本") Integer revision,
        @Schema(description = "报告schema版本；索引不代表正文核验通过") Integer schemaVersion,
        @Schema(description = "不可变报告正文摘要") String reportHash,
        @Schema(description = "生成时间，保持部署服务本地时间") LocalDateTime generatedAt,
        @Schema(description = "该报告冻结的观察截止时间") LocalDateTime observationCutoffAt,
        @Schema(description = "是否与既有人工决定的版本及摘要完全一致") boolean decisionReference) { }
