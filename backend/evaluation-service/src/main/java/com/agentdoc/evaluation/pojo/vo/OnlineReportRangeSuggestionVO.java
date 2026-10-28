package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 服务端当前范围建议，只填入表单，仍需显式确认生成报告。 */
public record OnlineReportRangeSuggestionVO(
        @Schema(description = "线上实验ID，十进制文本") String experimentId,
        @Schema(description = "读取时已接受的分配序号，十进制文本") String assignmentCutoffSequence,
        @Schema(description = "服务端当前本地时间，截断到毫秒") LocalDateTime observationCutoffAt,
        @Schema(description = "实际服务端JVM时区ID；历史无偏移时间不据此重新转换") String timeZone) { }
