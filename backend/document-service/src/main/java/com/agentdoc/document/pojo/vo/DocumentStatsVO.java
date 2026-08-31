package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间文档数量统计。
 *
 * @param totalCount 当前空间中正常文档总数
 * @param countAsOfLastMonth 截至上月月底创建的正常文档数
 */
@Schema(description = "空间文档数量统计")
public record DocumentStatsVO(

        @Schema(description = "当前空间正常文档总数")
        long totalCount,

        @Schema(description = "截至上月月底创建的正常文档数")
        long countAsOfLastMonth
) {
}
