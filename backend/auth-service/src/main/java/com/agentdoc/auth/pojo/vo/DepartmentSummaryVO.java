package com.agentdoc.auth.pojo.vo;

import com.agentdoc.auth.pojo.entity.DepartmentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 部门最小展示信息。
 */
@Schema(description = "部门摘要")
public record DepartmentSummaryVO(
        @Schema(description = "部门 ID") Long id,
        @Schema(description = "部门名称") String name,
        @Schema(description = "部门编码") String code
) {
    public static DepartmentSummaryVO from(DepartmentEntity department) {
        return department == null ? null
                : new DepartmentSummaryVO(department.getId(), department.getName(), department.getCode());
    }
}
