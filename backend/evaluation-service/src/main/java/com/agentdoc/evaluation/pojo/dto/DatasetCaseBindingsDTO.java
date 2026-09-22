package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_DATASET_CASE_COUNT;

/**
 * 数据集版本用例批量绑定DTO
 * <p>
 * 全量替换数据集草稿版本绑定的用例集合；传入完整列表，服务端以新列表覆盖原有绑定。
 * 仅允许对 DRAFT 状态的数据集版本执行，已发布(LIVE)版本冻结绑定关系不可修改。
 *
 * @param cases 数据集绑定的用例条目列表，不可为空，长度受上限约束
 */
public record DatasetCaseBindingsDTO(
        @NotEmpty(message = "绑定用例列表不能为空")
        @Size(max = MAX_DATASET_CASE_COUNT, message = "绑定用例数量超出上限")
        List<@Valid DatasetCaseBindingDTO> cases
) {
        public static final int MAX_DATASET_CASE_COUNT = 1000;
}