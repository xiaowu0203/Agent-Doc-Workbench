package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** 审批通过时采用的内容决议。 */
@Schema(description = "变更接受方式")
public enum ChangeRequestResolutionType {
    /** 接受原提案全部内容。 */
    ALL,
    /** 接受部分 Diff 块，正文由审批端基于预览计算并提交。 */
    PARTIAL,
    /** 审批人编辑提案后接受。 */
    EDITED
}
