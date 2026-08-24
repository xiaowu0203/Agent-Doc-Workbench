package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.agentdoc.document.pojo.vo.DocumentVersionDetailVO;
import com.agentdoc.document.pojo.vo.DocumentVersionVO;
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

    /**
     * 创建文档版本快照实体。
     */
    public static DocumentVersionEntity create(Long documentId, Long versionNo, String content,
                                               String changeSummary, Long userId) {
        DocumentVersionEntity entity = new DocumentVersionEntity();
        entity.setDocumentId(documentId);
        entity.setVersionNo(versionNo);
        entity.setContent(content);
        entity.setChangeSummary(changeSummary);
        entity.setCreatedBy(userId);
        return entity;
    }

    /**
     * 转换为版本列表视图对象（不含正文快照）。
     * @return 版本列表视图
     */
    public DocumentVersionVO toVO() {
        return new DocumentVersionVO(getId(), documentId, versionNo, changeSummary, createdBy, getCreatedAt());
    }

    /**
     * 转换为版本详情视图对象（含正文快照）。
     * @return 版本详情视图
     */
    public DocumentVersionDetailVO toDetailVO() {
        return new DocumentVersionDetailVO(documentId, versionNo, content, changeSummary, createdBy);
    }
}
