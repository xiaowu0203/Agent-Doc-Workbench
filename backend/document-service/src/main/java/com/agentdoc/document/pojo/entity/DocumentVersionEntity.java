package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档版本快照实体。
 * 注意：本表无 updated_at 列，故继承 {@link BaseEntity}（id/createdAt）并自行声明逻辑删除字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_version")
@Schema(description = "文档版本快照实体")
public class DocumentVersionEntity extends BaseEntity {

    @Schema(description = "文档 ID")
    private Long documentId;

    @Schema(description = "版本号（从 1 开始递增）")
    private Long versionNo;

    @Schema(description = "该版本 Markdown 快照")
    private String content;

    @Schema(description = "变更摘要")
    private String changeSummary;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;

    @Schema(description = "逻辑删除标记：0 未删除 / 1 已删除")
    @TableLogic
    private Integer deleted;
}
