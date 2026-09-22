package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TestCaseEvaluatorBindingsDTO(@NotEmpty List<@Valid TestCaseEvaluatorBindingDTO> evaluators) {
}
