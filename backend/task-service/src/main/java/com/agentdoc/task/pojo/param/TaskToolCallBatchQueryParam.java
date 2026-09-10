package com.agentdoc.task.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 批量查询任务工具调用明细的参数。
 */
@Schema(description = "批量查询任务工具调用明细参数")
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskToolCallBatchQueryParam extends PageParam {

    @NotNull
    @Schema(description = "空间 ID")
    private Long spaceId;

    @NotEmpty
    @Size(max = 100)
    @Schema(description = "任务 ID 集合")
    private List<Long> taskIds;
}
