package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 版本对比视图对象（v0.1 简化文本级：返回两版本快照，diff 高亮由前端渲染）。
 */
@Schema(description = "版本对比结果")
public record VersionCompareVO(

        @Schema(description = "对比源版本")
        DocumentVersionDetailVO from,

        @Schema(description = "对比目标版本")
        DocumentVersionDetailVO to
) {
}
