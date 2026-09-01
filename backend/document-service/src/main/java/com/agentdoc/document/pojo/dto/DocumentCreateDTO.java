package com.agentdoc.document.pojo.dto;

import com.agentdoc.document.constant.DocumentConstant;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建文档请求参数。
 */
@Schema(description = "创建文档请求")
public record DocumentCreateDTO(

        @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "空间 ID 不能为空")
        Long spaceId,

        @Schema(description = "所属目录 ID，null 为空间根层")
        Long directoryId,

        @Schema(description = "文档标题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "文档标题不能为空")
        @Size(max = 200, message = "文档标题最长 200 字符")
        String title,

        @Schema(description = "文档类型：FORMAL 正式 / DRAFT 草稿", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "文档类型不能为空")
        DocType docType,

        @Schema(description = "Markdown 初始内容")
        String content
) {

    /**
     * 转换为文档实体（创建人由服务层指定）。
     * @param userId 创建人用户 ID
     * @return 文档实体
     */
    public DocumentEntity toEntity(Long userId) {
        DocumentEntity entity = new DocumentEntity();
        entity.setSpaceId(spaceId);
        entity.setDirectoryId(directoryId);
        entity.setTitle(title);
        entity.setDocType(docType.getCode());
        entity.setContent(content);
        entity.setVersion(DocumentConstant.INITIAL_VERSION);
        entity.setStatus(DocStatus.NORMAL.getCode());
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        return entity;
    }
}
