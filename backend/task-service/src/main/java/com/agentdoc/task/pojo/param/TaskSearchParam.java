package com.agentdoc.task.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.task.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_SEARCH_KEYWORD_LENGTH;

/**
 * 任务列表分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务列表分页查询参数")
public class TaskSearchParam extends PageParam {

    @NotNull
    @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long spaceId;

    @Schema(description = "任务状态")
    private TaskStatus status;

    @Schema(description = "Agent ID")
    private Long agentId;

    @Schema(description = "实际调用模型 ID")
    private Long modelId;

    @Schema(description = "目标文档 ID")
    private Long documentId;

    @Schema(description = "执行开始时间下限（含）")
    private LocalDateTime startedFrom;

    @Schema(description = "执行开始时间上限（不含）")
    private LocalDateTime startedTo;

    @Size(max = MAX_TASK_SEARCH_KEYWORD_LENGTH)
    @Schema(description = "任务编号、名称或指令关键字")
    private String keyword;
}
