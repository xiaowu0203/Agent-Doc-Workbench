package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 与用例冻结依据一致的来源定位信息，不返回输入、执行快照或文档正文。 */
public record TestCaseSourceVO(
        @Schema(description = "测试用例版本ID") Long testCaseVersionId,
        @Schema(description = "所属空间ID") Long spaceId,
        @Schema(description = "冻结来源任务ID") Long sourceTaskId,
        @Schema(description = "已匹配的冻结输入哈希") String sourceInputHash,
        @Schema(description = "冻结来源所属文档ID") Long documentId,
        @Schema(description = "当前文档名称，仅辅助定位，不表示冻结时名称") String documentTitle
) { }
