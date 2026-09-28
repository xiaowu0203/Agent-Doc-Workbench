package com.agentdoc.evaluation.pojo.dto;

import com.agentdoc.evaluation.enums.ExperimentDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 针对指定不可变报告版本作人工结论。 */
public record ExperimentDecisionDTO(@NotNull @Positive Integer reportRevision,
                                    @NotNull ExperimentDecision decision,
                                    @NotBlank @Size(max = 1000) String reason) { }
