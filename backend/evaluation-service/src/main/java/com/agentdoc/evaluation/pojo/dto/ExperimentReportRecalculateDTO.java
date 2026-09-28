package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 显式重算报告的幂等请求。 */
public record ExperimentReportRecalculateDTO(@NotBlank @Size(max = 191) String clientRequestKey) { }
