package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 测试用例版本评估器批量绑定DTO
 * <p>
 * 全量替换草稿测试用例版本绑定的评估器集合；传入完整列表，服务端以新列表覆盖原有绑定。
 * 仅允许对 DRAFT 状态的用例版本执行，已发布(LIVE)版本冻结绑定关系不可修改。
 *
 * @param evaluators 绑定的评估器条目列表，不可为空
 */
@Schema(description = "测试用例版本评估器批量绑定")
public record TestCaseEvaluatorBindingsDTO(
        @NotEmpty(message = "评估器绑定列表不能为空")
        @Valid
        @Schema(description = "评估器绑定条目集合")
        List<TestCaseEvaluatorBindingDTO> evaluators
) {}