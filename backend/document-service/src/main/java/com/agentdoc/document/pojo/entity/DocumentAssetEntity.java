package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档图片附件元数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_asset")
@Schema(description = "文档图片附件")
public class DocumentAssetEntity extends BaseLogicDeleteEntity {

    @Schema(description = "文档 ID")
    private Long documentId;

    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "对象存储 Key")
    private String objectKey;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "文件 MIME 类型")
    private String contentType;

    @Schema(description = "文件大小，单位字节")
    private Long sizeBytes;

    @Schema(description = "上传人用户 ID")
    private Long createdBy;
}
