package com.agentdoc.document.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 空间权限目录实体。
 */
@Data
@TableName("permission")
@Schema(description = "空间权限目录实体")
public class PermissionEntity {

    @TableId
    @Schema(description = "稳定权限标识符")
    private String code;

    @Schema(description = "权限展示名称")
    private String name;

    @Schema(description = "权限分类")
    private String category;

    @Schema(description = "权限说明")
    private String description;

    @Schema(description = "展示顺序")
    private Integer sortOrder;
}
