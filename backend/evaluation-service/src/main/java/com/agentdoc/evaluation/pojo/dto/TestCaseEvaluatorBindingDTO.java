package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 测试用例版本绑定评估器条目DTO
 * <p>
 * 用于给草稿测试用例版本绑定评估器；支持配置预期输出模板，sortOrder控制评估器执行顺序。
 * 仅可绑定已发布的 EvaluatorVersion，草稿评估器版本不允许绑定。
 *
 * @param evaluatorVersionId 评估器版本主键ID
 * @param expectedJson       预期指标输出JSON模板，可选，用于结果校验对比
 * @param sortOrder          评估器执行顺序，数值越小越先执行
 */
@Schema(description = "测试用例绑定评估器条目")
public record TestCaseEvaluatorBindingDTO(
        @NotNull(message = "evaluatorVersionId 不能为空")
        @Schema(description = "评估器版本ID")
        Long evaluatorVersionId,

        @Schema(description = "预期指标输出JSON模板，选填")
        String expectedJson,

        @NotNull(message = "sortOrder 不能为空")
        @Schema(description = "评估器执行顺序，数值越小优先级越高")
        Integer sortOrder
) {}