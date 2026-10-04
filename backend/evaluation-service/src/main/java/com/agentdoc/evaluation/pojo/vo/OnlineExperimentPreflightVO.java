package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description = "OnlineExperimentPreflightVO")
public record OnlineExperimentPreflightVO(
        @Schema(description = "实验身份") String experimentId,
        @Schema(description = "冻结清单摘要") String manifestHash,
        @Schema(description = "UTC检查时点") String checkedAt,
        @Schema(description = "检查证明摘要") String proofHash,
        @Schema(description = "UTC失效时点") String expiresAt,
        @Schema(description = "草案完整性") boolean draftEligible,
        @Schema(description = "本批固定false") boolean startable,
        @Schema(description = "基线范围文档数") int baselineDocumentCount,
        @Schema(description = "候选范围文档数") int candidateDocumentCount,
        @Schema(description = "范围分配诊断") OnlineSrmVO srm,
        @Schema(description = "理论最大Token文本，不允许溢出") String plannedUpperTokenBudget,
        @Schema(description = "未接入估算时为空") String historicalEstimate,
        @Schema(description = "阻塞/提示项") List<Issue> issues
) {
    /** @param code 稳定原因 @param severity ERROR/WARNING
     * @param documentId 可选文档身份 @param ruleKey 可选规则身份 */
    public record Issue(String code, String severity, String documentId, String ruleKey) { }
}
