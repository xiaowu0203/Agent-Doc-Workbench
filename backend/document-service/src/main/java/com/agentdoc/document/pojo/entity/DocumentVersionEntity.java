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

    @Schema(description = "版本来源：UNKNOWN / CREATE / HUMAN_EDIT / AGENT_DRAFT / APPROVAL_MERGE / ROLLBACK")
    private String sourceType;

    @Schema(description = "版本操作主体：UNKNOWN / HUMAN / AGENT")
    private String actorType;

    @Schema(description = "版本操作主体 ID")
    private Long actorId;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;

    @Schema(description = "审批合并来源变更请求 ID")
    private Long sourceChangeRequestId;

    @Schema(description = "来源任务 ID")
    private Long sourceTaskId;

    @Schema(description = "回滚来源版本号")
    private Long rollbackFromVersion;

    @Schema(description = "正文快照 SHA-256")
    private String contentSha256;

    @Schema(description = "逻辑删除标记：0 未删除 / 1 已删除")
    @TableLogic
    private Integer deleted;

    /**
     * 创建文档版本快照实体。
     */
    public static DocumentVersionEntity create(Long documentId, Long versionNo, String content,
                                               String changeSummary, String sourceType,
                                               String actorType, Long actorId,
                                               Long sourceChangeRequestId, Long sourceTaskId,
                                               Long rollbackFromVersion, String contentSha256) {
        DocumentVersionEntity entity = new DocumentVersionEntity();
        entity.setDocumentId(documentId);
        entity.setVersionNo(versionNo);
        entity.setContent(content);
        entity.setChangeSummary(changeSummary);
        entity.setSourceType(sourceType);
        entity.setActorType(actorType);
        entity.setActorId(actorId);
        entity.setCreatedBy(actorId);
        entity.setSourceChangeRequestId(sourceChangeRequestId);
        entity.setSourceTaskId(sourceTaskId);
        entity.setRollbackFromVersion(rollbackFromVersion);
        entity.setContentSha256(contentSha256);
        return entity;
    }
}
