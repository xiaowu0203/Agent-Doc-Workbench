package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_DATASET_CASE_COUNT;
public record DatasetCaseBindingsDTO(@NotEmpty @Size(max = MAX_DATASET_CASE_COUNT)
        List<@Valid DatasetCaseBindingDTO> cases) { }
