package com.agentdoc.document.entity;

import com.agentdoc.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档版本快照实体。
 * 注意：本表无 updated_at 列，故继承 {@link BaseEntity}（id/createdAt）并自行声明逻辑删除字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_version")
public class DocumentVersionEntity extends BaseEntity {

    private Long documentId;

    /** 版本号（从 1 开始递增） */
    private Long versionNo;

    /** 该版本 Markdown 快照 */
    private String content;

    private String changeSummary;

    private Long createdBy;

    @TableLogic
    private Integer deleted;
}
