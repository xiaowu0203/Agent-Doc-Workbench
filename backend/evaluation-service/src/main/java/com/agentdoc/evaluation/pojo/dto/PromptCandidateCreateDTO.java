package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Prompt 候选变体创建参数。 */
public record PromptCandidateCreateDTO(
        @NotBlank @Size(max = 64) String variantKey,
        @NotBlank String agentPrompt) {
}
