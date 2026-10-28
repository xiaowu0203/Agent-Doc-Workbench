package com.agentdoc.task.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.task.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import static com.agentdoc.task.constant.TaskConstant.MAX_TREND_DAYS;

/** 按工具调用时间查询全部明细，不依赖执行记录页。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskToolCallSearchParam extends PageParam {
    @Schema(description = "当前空间ID") private Long spaceId;
    @Schema(description = "工具调用开始时间下限，含") private LocalDateTime startAt;
    @Schema(description = "工具调用开始时间上限，不含") private LocalDateTime endAt;
    @Schema(description = "Agent ID") private Long agentId;
    @Schema(description = "模型ID") private Long modelId;
    @Schema(description = "任务状态，映射到执行状态") private TaskStatus status;
    @Schema(description = "执行模式") private TaskExecutionMode executionMode;
    @Schema(description = "是否参与线上实验，null表示全部") private Boolean onlineParticipation;

    @Override public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0 || startAt == null || endAt == null
                || !startAt.isBefore(endAt) || endAt.isAfter(startAt.plusDays(MAX_TREND_DAYS))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具明细查询空间或时间范围无效");
        }
    }
}
