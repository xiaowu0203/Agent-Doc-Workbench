package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 当前页资源的版本元数据，不包含配置与冻结业务输入。 */
public record CatalogVersionSummaryVO(
        @Schema(description = "所属主资源ID") Long resourceId,
        @Schema(description = "版本总数，含归档版本") Long versionCount,
        @Schema(description = "草稿版本数") Long draftCount,
        @Schema(description = "最新已发布版本ID，无发布版本时为空") Long latestPublishedVersionId,
        @Schema(description = "最新已发布版本号，按版本号选择") Integer latestPublishedVersionNo,
        @Schema(description = "该发布版本的绑定数；数据集为启用用例，用例为评估器，评估器为引用用例版本数；无发布版本时为空") Long publishedBindingCount) {
    public static CatalogVersionSummaryVO empty(Long resourceId) {
        return new CatalogVersionSummaryVO(resourceId, 0L, 0L, null, null, null);
    }
}
