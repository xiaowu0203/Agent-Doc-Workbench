package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 创建数据集草稿版本DTO
 * <p>
 * 基于已有数据集新建 DRAFT；可显式复制同数据集已发布版本的绑定。
 *
 * @param datasetId 所属数据集主键ID
 * @param copyFromVersionId 可选的绑定复制来源，省略时创建空草稿
 */
public record DatasetVersionCreateDTO(
        @NotNull(message = "datasetId 不能为空")
        @Schema(description = "所属数据集 ID") Long datasetId,
        @Positive(message = "复制来源版本 ID 必须为正整数")
        @Schema(description = "同数据集已发布来源版本 ID；省略时不复制绑定") Long copyFromVersionId
) {}
