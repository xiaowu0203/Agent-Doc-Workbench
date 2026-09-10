package com.agentdoc.auth.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 部门直属成员数量查询结果。
 */
@Data
@Schema(description = "部门直属成员数量查询结果")
public class DepartmentMemberCountVO {

    @Schema(description = "部门 ID")
    private Long departmentId;

    @Schema(description = "直属成员数量")
    private Long memberCount;
}
