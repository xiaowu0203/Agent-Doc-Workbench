package com.agentdoc.task.pojo.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 普通工作台页面批量补任务名称，不用于隐藏分组评审。 */
public record TaskNamesSearchParam(
        @NotNull @Positive @Schema(description = "当前空间ID") Long spaceId,
        @NotNull @Size(min = 1, max = 100) @Schema(description = "当前页任务ID，最多100个") List<@NotNull @Positive Long> taskIds) {
}
