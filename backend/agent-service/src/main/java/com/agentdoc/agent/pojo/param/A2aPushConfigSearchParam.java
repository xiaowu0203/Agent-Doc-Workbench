package com.agentdoc.agent.pojo.param;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A2A 任务推送配置列表查询参数。
 */
@Schema(description = "A2A 推送配置列表查询参数")
public record A2aPushConfigSearchParam(
        @Schema(description = "每页配置数量，默认 50")
        Integer pageSize,

        @Schema(description = "分页游标")
        String pageToken) {

    public static final int DEFAULT_PAGE_SIZE = 50;

    public A2aPushConfigSearchParam {
        if (pageSize == null) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
    }
}
