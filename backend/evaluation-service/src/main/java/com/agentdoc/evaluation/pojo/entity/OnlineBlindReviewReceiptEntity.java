package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_blind_review_receipt")
public class OnlineBlindReviewReceiptEntity extends BaseEntity {
    @Schema(description = "实验") private Long experimentId;
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "原分配") private Long assignmentId;
    @Schema(description = "实际人类评审者") private Long actorId;
    @Schema(description = "原Task") private Long taskId;
    @Schema(description = "实际Execution") private Long executionId;
    @Schema(description = "经核验的整体冻结业务输入摘要") private String inputHash;
    @Schema(description = "原始文本或完整变更集合") private String originalEvidenceType;
    @Schema(description = "原始证据身份") private Long originalEvidenceId;
    @Schema(description = "原始证据摘要") private String originalEvidenceHash;
    @Schema(description = "资格策略版本") private Integer policyVersion;
    @Schema(description = "实际读取时间") private LocalDateTime capturedAt;
    @Schema(description = "不可变资格证明摘要") private String sourceHash;
}
