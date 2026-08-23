package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 变更请求实体（审批流）。
 * <p>转换逻辑（实体 ↔ 视图 / changes JSON）见 {@code ChangeRequestConvertor}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("change_request")
@Schema(description = "变更请求实体")
public class ChangeRequestEntity extends BaseLogicDeleteEntity {

    @Schema(description = "目标文档 ID")
    private Long documentId;

    @Schema(description = "类型：1 正式 / 2 草稿")
    private Integer requestType;

    @Schema(description = "结构化变更（JSON 数组），Phase 2 解析为 Diff 明细")
    private String changes;

    @Schema(description = "目标文档基线版本号（合并时校验防并发覆盖）")
    private Long baseVersion;

    @Schema(description = "状态：0 待审批 / 1 已通过 / 2 已拒绝 / 3 已合并 / 4 已退回")
    private Integer status;

    @Schema(description = "来源任务 ID")
    private Long sourceTaskId;

    @Schema(description = "提交人（用户或 Agent ID）")
    private Long proposedBy;

    @Schema(description = "审批意见")
    private String reviewComment;
}
