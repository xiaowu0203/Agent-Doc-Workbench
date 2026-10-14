package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description = "OnlineExperimentCreateDTO")
public record OnlineExperimentCreateDTO(
        @Schema(description = "操作者请求幂等键") String clientRequestKey,
        @Schema(description = "空间身份") String spaceId,
        @Schema(description = "Agent 身份") String agentId,
        @Schema(description = "实验名称") String name,
        @Schema(description = "冻结文档范围") List<String> documentIds,
        @Schema(description = "候选 Agent 提示词") String candidateAgentPrompt,
        @Schema(description = "候选文档分配概率万分比") Integer candidateWeightBps,
        @Schema(description = "总授权 Token 正整数文本") String authorizedTokenBudget,
        @Schema(description = "任务数上限") Integer maxTaskCount,
        @Schema(description = "单任务 Token 上限文本") String perTaskTokenLimit,
        @Schema(description = "分配窗口秒，空采用默认") Integer assignmentWindowSeconds,
        @Schema(description = "观察窗口秒，空采用默认") Integer completionObservationSeconds,
        @Schema(description = "启动前分析计划") OnlineAnalysisPlanDTO analysisPlan,
        @Schema(description = "冻结规则表") List<OnlineRuleBindingDTO> rules
) { }
