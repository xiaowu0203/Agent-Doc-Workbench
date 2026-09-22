package com.agentdoc.evaluation.pojo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建评估器草稿版本DTO
 * <p>
 * 为评估器主记录新建草稿版本，承载指标契约配置；
 * configJson 为评估器配置JSON，草稿版本不可直接绑定用例，发布冻结后生效。
 *
 * @param evaluatorId         所属评估器主键ID
 * @param configSchemaVersion 配置JSON对应的Schema版本号，用于向后兼容解析
 * @param configJson          评估器指标契约配置JSON字符串
 * @param resultSchemaVersion 输出指标结果Schema版本号
 */
@Schema(description = "创建评估器草稿版本")
public record EvaluatorVersionCreateDTO(
        @NotNull(message = "evaluatorId 不能为空")
        @Schema(description = "所属评估器ID")
        Long evaluatorId,

        @NotNull(message = "configSchemaVersion 不能为空")
        @Schema(description = "配置Schema版本，用于配置解析兼容")
        Integer configSchemaVersion,

        @NotBlank(message = "configJson 不能为空")
        @Schema(description = "评估器指标契约配置JSON")
        String configJson,

        @NotNull(message = "resultSchemaVersion 不能为空")
        @Schema(description = "评估结果输出Schema版本")
        Integer resultSchemaVersion
) {}