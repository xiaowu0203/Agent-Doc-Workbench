package com.agentdoc.auth.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台组织部门实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("department")
@Schema(description = "平台组织部门实体")
public class DepartmentEntity extends BaseLogicDeleteEntity {

    @Schema(description = "上级部门 ID，0 为组织根节点")
    private Long parentId;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "稳定部门编码")
    private String code;

    @Schema(description = "负责人用户 ID")
    private Long leaderUserId;

    @Schema(description = "同级排序值")
    private Integer sortOrder;

    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
}
