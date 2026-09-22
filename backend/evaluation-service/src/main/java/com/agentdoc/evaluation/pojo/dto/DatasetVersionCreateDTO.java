package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotNull;

/**
 * 创建数据集草稿版本DTO
 * <p>
 * 基于已有数据集主记录新建一条 DRAFT 版本，新建后无绑定用例，
 * 需要调用替换绑定接口追加用例，发布后转为正式 LIVE 版本。
 *
 * @param datasetId 所属数据集主键ID
 */
public record DatasetVersionCreateDTO(
        @NotNull(message = "datasetId 不能为空")
        Long datasetId
) {}