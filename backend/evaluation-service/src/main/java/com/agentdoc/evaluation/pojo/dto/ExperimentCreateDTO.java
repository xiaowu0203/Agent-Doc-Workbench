package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 创建离线 Experiment。 */
public record ExperimentCreateDTO(
        @NotNull Long spaceId,
        @NotBlank @Size(max = 191) String clientRequestKey,
        @NotNull Long datasetVersionId,
        @NotNull @Size(min = 1, max = 19) List<@Valid PromptCandidateCreateDTO> candidateVariants) {
}
