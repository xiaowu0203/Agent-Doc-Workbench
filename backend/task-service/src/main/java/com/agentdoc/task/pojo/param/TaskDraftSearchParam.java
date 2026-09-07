package com.agentdoc.task.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_SEARCH_KEYWORD_LENGTH;

/**
 * 当前用户任务草稿分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务草稿分页查询参数")
public class TaskDraftSearchParam extends PageParam {

    @NotNull
    @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long spaceId;

    @Size(max = MAX_TASK_SEARCH_KEYWORD_LENGTH)
    @Schema(description = "任务名称或指令关键字")
    private String keyword;
}
