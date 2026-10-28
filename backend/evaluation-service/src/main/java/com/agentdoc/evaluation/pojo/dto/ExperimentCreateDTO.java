package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_DISPLAY_NAME_LENGTH;

import java.util.List;

/** 创建离线 Experiment。 */
public record ExperimentCreateDTO(
        @NotNull Long spaceId,
        @NotBlank @Size(max = 191) String clientRequestKey,
        @NotNull Long datasetVersionId,
        @NotNull @Size(min = 1, max = 19) List<@Valid PromptCandidateCreateDTO> candidateVariants,
        @Size(max = MAX_DISPLAY_NAME_LENGTH)
        @Schema(description = "可选离线实验名称，不改变 manifest 或报告身份") String name) {
}
