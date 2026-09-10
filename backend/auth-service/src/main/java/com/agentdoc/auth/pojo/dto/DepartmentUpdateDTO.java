package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_DEPARTMENT_NAME_LENGTH;

/**
 * 修改部门请求。部门编码创建后保持不变。
 */
@Schema(description = "修改部门请求")
public record DepartmentUpdateDTO(
        @NotBlank(message = "部门名称不能为空")
        @Size(max = MAX_DEPARTMENT_NAME_LENGTH, message = "部门名称过长")
        @Schema(description = "部门名称")
        String name,

        @Schema(description = "上级部门 ID；为空或 0 表示组织根节点")
        Long parentId,

        @Schema(description = "负责人用户 ID；为空表示清除负责人")
        Long leaderUserId,

        @Min(value = 0, message = "排序值不能小于 0")
        @Schema(description = "同级排序值")
        Integer sortOrder,

        @Min(value = 0, message = "部门状态无效")
        @Max(value = 1, message = "部门状态无效")
        @Schema(description = "状态：0 禁用 / 1 启用")
        Integer status
) {
}
