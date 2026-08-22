package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档实体（树形目录 / 草稿正式双模式）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document")
@Schema(description = "文档实体")
public class DocumentEntity extends BaseLogicDeleteEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "父目录 ID，0 为根")
    private Long parentId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "Markdown 内容")
    private String content;

    @Schema(description = "类型：1 正式 / 2 草稿")
    private Integer docType;

    @Schema(description = "当前版本号（业务层维护，非乐观锁）")
    private Long version;

    @Schema(description = "状态：1 正常 / 0 归档")
    private Integer status;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;

    @Schema(description = "最后更新人用户 ID")
    private Long updatedBy;
}
