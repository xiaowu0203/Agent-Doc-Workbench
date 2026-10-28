package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 从已校验的不可变实验清单投影来源数量，不修改报告正文或哈希。 */
public record ExperimentSourceSummaryVO(
        @Schema(description = "实验ID") Long experimentId,
        @Schema(description = "所属空间ID") Long spaceId,
        @Schema(description = "校验过的冻结清单摘要") String manifestHash,
        @Schema(description = "冻结用例数") int caseCount,
        @Schema(description = "已知来源任务按ID去重的数量，不代表统计独立性") long distinctSourceTaskCount,
        @Schema(description = "缺少合法来源任务身份的用例数") long missingSourceCaseCount) {
}
