package com.agentdoc.evaluation.pojo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建数据集DTO
 *
 * @param spaceId     空间ID，数据集归属空间
 * @param name        数据集名称，非空，最大长度200
 * @param description 数据集描述，可选，最大长度1000
 */
@Schema(description = "创建数据集")
public record EvaluationDatasetCreateDTO(
        @NotNull(message = "spaceId 不能为空")
        @Schema(description = "所属空间ID")
        Long spaceId,

        @NotBlank(message = "数据集名称不能为空")
        @Size(max = 200, message = "数据集名称不能超过200字符")
        @Schema(description = "数据集名称")
        String name,

        @Size(max = 1000, message = "数据集描述不能超过1000字符")
        @Schema(description = "数据集描述，选填")
        String description
) {}
