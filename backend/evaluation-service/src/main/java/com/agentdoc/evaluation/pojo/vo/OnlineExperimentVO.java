package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
@Schema(description = "OnlineExperimentVO")
public record OnlineExperimentVO(
        @Schema(description = "摘要投影") @JsonUnwrapped OnlineExperimentSummaryVO summary,
        @Schema(description = "基线模板证明") AgentOnlineConfigPairVO.TemplateIdentity baseline,
        @Schema(description = "候选模板证明") AgentOnlineConfigPairVO.TemplateIdentity candidate,
        @Schema(description = "冻结依赖摘要") String dependencyHash,
        @Schema(description = "固定分析参数") JsonNode analysisPlan,
        @Schema(description = "固定预算上限") JsonNode budgetPlan,
        @Schema(description = "固定窗口参数") JsonNode windowPlan,
        @Schema(description = "固定保护策略") JsonNode protectionPlan,
        @Schema(description = "空间占位") Integer activeSlot,
        @Schema(description = "实际首次启动") LocalDateTime startedAt,
        @Schema(description = "分配截止") LocalDateTime assignmentDeadline,
        @Schema(description = "观察截止") LocalDateTime observationDeadline,
        @Schema(description = "人工结论") String decision
) { }
