package com.agentdoc.evaluation.pojo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建评估器主记录DTO
 * <p>
 * 新建评估器主体，evaluatorKey为业务唯一标识；创建后主记录无指标契约定义，
 * 需要继续创建草稿版本配置指标契约，发布后版本才能绑定到测试用例版本。
 *
 * @param spaceId       归属空间ID
 * @param name          评估器展示名称，非空，最大200字符
 * @param evaluatorKey  评估器业务唯一Key，非空，最大64字符，遥测埋点使用
 * @param description   评估器描述说明，选填，最大1000字符
 */
@Schema(description = "创建评估器")
public record EvaluatorCreateDTO(
        @NotNull(message = "spaceId 不能为空")
        @Schema(description = "所属空间ID")
        Long spaceId,

        @NotBlank(message = "评估器名称不能为空")
        @Size(max = 200, message = "评估器名称不能超过200字符")
        @Schema(description = "评估器展示名称")
        String name,

        @NotBlank(message = "evaluatorKey 不能为空")
        @Size(max = 64, message = "evaluatorKey 不能超过64字符")
        @Schema(description = "评估器业务唯一标识，用于遥测与指标检索")
        String evaluatorKey,

        @Size(max = 1000, message = "评估器描述不能超过1000字符")
        @Schema(description = "评估器描述，选填")
        String description
) {}