package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DatasetVersion 的测试用例绑定摘要")
public record DatasetCaseBindingVO(
        @Schema(description = "绑定 ID") Long id,
        @Schema(description = "测试用例版本 ID") Long testCaseVersionId,
        @Schema(description = "测试用例主资源 ID") Long testCaseId,
        @Schema(description = "测试用例名称") String testCaseName,
        @Schema(description = "测试用例版本号") Integer versionNo,
        @Schema(description = "测试用例版本状态") String status,
        @Schema(description = "绑定顺序") Integer sortOrder,
        @Schema(description = "是否启用") Boolean enabled
) { }
