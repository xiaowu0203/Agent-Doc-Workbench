package com.agentdoc.task.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 变更请求实体（审批流）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("change_request")
public class ChangeRequestEntity extends BaseLogicDeleteEntity {

    private Long documentId;

    /** 类型：1 正式 / 2 草稿 */
    private Integer requestType;

    /** 结构化变更（JSON 数组），Phase 2 解析为 Diff 明细 */
    private String changes;

    /** 状态：0 待审批 / 1 已通过 / 2 已拒绝 / 3 已合并 / 4 已退回 */
    private Integer status;

    private Long sourceTaskId;

    /** 提交人（用户或 Agent ID） */
    private Long proposedBy;

    private String reviewComment;
}
