package com.agentdoc.task.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 空间审计日志分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "空间审计日志查询条件")
public class AuditLogSearchParam extends PageParam {

    @NotNull
    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "主体类型：1 人 / 2 Agent")
    private Integer actorType;

    @Size(max = 64)
    @Schema(description = "操作行为")
    private String action;

    @Size(max = 64)
    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "目标 ID")
    private Long targetId;

    @Schema(description = "创建时间下限（含）")
    private LocalDateTime createdFrom;

    @Schema(description = "创建时间上限（不含）")
    private LocalDateTime createdTo;
}

