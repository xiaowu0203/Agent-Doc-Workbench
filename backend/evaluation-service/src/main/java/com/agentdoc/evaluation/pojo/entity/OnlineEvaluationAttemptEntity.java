package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_evaluation_attempt")
@Schema(description = "线上冻结规则的追加评价尝试")
public class OnlineEvaluationAttemptEntity extends BaseEntity {
    @Schema(description = "原分配身份") private Long assignmentId;
    @Schema(description = "空间身份") private Long spaceId;
    @Schema(description = "冻结发布版本") private Long evaluatorVersionId;
    @Schema(description = "assignment 范围的追加序号") private Integer attemptNo;
    @Schema(description = "幂等请求键") private String clientRequestKey;
    @Schema(description = "请求作用域摘要") private String requestHash;
    @Schema(description = "完成状态") private String status;
    @Schema(description = "冻结规则键") private String ruleKey;
    @Schema(description = "本次评价对应文档的冻结 expected 摘要") private String expectedHash;
    @Schema(description = "结果身份") private Long resultId;
    @Schema(description = "脱敏结果原因") private String reasonCode;
    @Schema(description = "评价开始时间") private LocalDateTime startedAt;
    @Schema(description = "评价完成时间") private LocalDateTime finishedAt;
    @Schema(description = "人类评价发起人") private Long createdBy;
}
