package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 隐藏分组完整评审；客户端不得再请求普通元数据作为盲态补充。 */
public record OnlineBlindReviewContextVO(
        @Schema(description = "原分配") String assignmentId,
        @Schema(description = "BLINDED/NON_BLINDED/UNKNOWN；站外知情仍须如实声明") String knowledgeStatus,
        @Schema(description = "是否具备服务器盲态资格") Boolean blindedEligible,
        @Schema(description = "经过整体输入摘要核验的冻结用户目标") OnlineReviewGoalVO goal,
        @Schema(description = "隐藏分组的完整原始评审正文") JsonNode originalContext) { }
