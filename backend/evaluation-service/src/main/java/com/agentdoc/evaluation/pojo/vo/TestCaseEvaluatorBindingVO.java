package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "TestCaseVersion 的评估器绑定摘要")
public record TestCaseEvaluatorBindingVO(
        @Schema(description = "绑定 ID") Long id,
        @Schema(description = "评估器版本 ID") Long evaluatorVersionId,
        @Schema(description = "评估器主资源 ID") Long evaluatorId,
        @Schema(description = "评估器名称") String evaluatorName,
        @Schema(description = "评估器版本号") Integer versionNo,
        @Schema(description = "评估器版本状态") String status,
        @Schema(description = "内置评估规则 key") String evaluatorKey,
        @Schema(description = "绑定顺序") Integer sortOrder,
        @Schema(description = "绑定级期望 JSON；保留原编辑载荷") String expectedJson
) { }
