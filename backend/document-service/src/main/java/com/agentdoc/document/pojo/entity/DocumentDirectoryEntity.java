package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档目录实体。目录只负责组织文档，不承载正文、版本和文档类型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_directory")
@Schema(description = "文档目录实体")
public class DocumentDirectoryEntity extends BaseLogicDeleteEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "父目录 ID，null 为根目录")
    private Long parentId;

    @Schema(description = "目录名称")
    private String title;

    @Schema(description = "状态：1 正常 / 0 归档")
    private Integer status;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;

    @Schema(description = "最后更新人用户 ID")
    private Long updatedBy;
}
