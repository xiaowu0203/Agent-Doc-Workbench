package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record CaseVersionNameVO(
        @Schema(description = "当前空间ID") Long spaceId,
        @Schema(description = "用例版本ID") Long versionId,
        @Schema(description = "用例主资源ID") Long testCaseId,
        @Schema(description = "当前目录用例名称，不代表冻结时名称") String name,
        @Schema(description = "版本号") Integer versionNo) { }
