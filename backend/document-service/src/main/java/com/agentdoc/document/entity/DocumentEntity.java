package com.agentdoc.document.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档实体（树形目录 / 草稿正式双模式）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document")
public class DocumentEntity extends BaseLogicDeleteEntity {

    private Long spaceId;

    /** 父目录 ID，0 为根 */
    private Long parentId;

    private String title;

    /** Markdown 内容 */
    private String content;

    /** 类型：1 正式 / 2 草稿 */
    private Integer docType;

    /** 当前版本号（普通版本列，由业务层维护，非乐观锁） */
    private Long version;

    /** 状态：1 正常 / 0 归档 */
    private Integer status;

    private Long createdBy;

    private Long updatedBy;
}
