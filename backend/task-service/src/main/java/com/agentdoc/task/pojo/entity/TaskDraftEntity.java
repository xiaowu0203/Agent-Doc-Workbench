package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_draft")
@Schema(description = "任务草稿实体")
public class TaskDraftEntity extends BaseLogicDeleteEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "Agent ID")
    private Long agentId;

    @Schema(description = "目标文档 ID")
    private Long documentId;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "任务指令")
    private String instruction;

    @Schema(description = "任务 Token 预算")
    private Long tokenBudget;

    @Schema(description = "读取范围：FULL / RANGES")
    private String readScope;

    @Schema(description = "关注区域 JSON")
    private String focusRegionsJson;

    @Schema(description = "草稿所有者用户 ID")
    private Long createdBy;
}
