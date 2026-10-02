package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.enums.ExperimentReportCompatibility;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportV1VO.SelectedRecordIdsVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportV1VO.ContentVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 不可变实验报告版本及其冻结计算输入。 */
public record ExperimentReportVO(
        @Schema(description = "实验 ID") Long experimentId,
        @Schema(description = "不可变报告版本") Integer revision,
        @Schema(description = "报告 schema 版本") Integer schemaVersion,
        @Schema(description = "冻结 manifest hash") String manifestHash,
        @Schema(description = "计算输入 hash") String calculationInputHash,
        @Schema(description = "选中记录；不兼容时为空") SelectedRecordIdsVO selectedRecordIds,
        @Schema(description = "v1 正文；不兼容时为空") ContentVO report,
        @Schema(description = "持久化原文 hash") String contentHash,
        @Schema(description = "生成人") Long generatedBy,
        @Schema(description = "生成时间") LocalDateTime generatedAt,
        @Schema(description = "是否兼容且完整") boolean compatible,
        @Schema(description = "兼容状态") ExperimentReportCompatibility compatibilityCode
) { }
