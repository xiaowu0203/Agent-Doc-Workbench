package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_evidence_reference")
@Schema(description = "评估证据最小引用")
public class EvaluationEvidenceReferenceEntity extends BaseEntity {
    @Schema(description = "主体类型 CASE_ATTEMPT/ONLINE_TASK") private String subjectType = "CASE_ATTEMPT";
    @Schema(description = "线上分配身份，离线为空") private Long onlineAssignmentId;
    @Schema(description = "线上评价尝试身份，离线为空") private Long onlineEvaluationAttemptId;
    @Schema(description = "线上 Task 身份，离线为空") private Long taskId;
    @Schema(description = "线上 Execution 身份，离线为空") private Long executionId;
    @Schema(description = "CaseAttempt ID") private Long caseAttemptId;
    @Schema(description = "EvaluationResult ID；执行型证据可为空") private Long resultId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "证据类型") private String evidenceType;
    @Schema(description = "证据业务 ID") private String businessId;
    @Schema(description = "证据内容 hash") private String contentHash;
    @Schema(description = "限长脱敏摘要") private String summary;
    @Schema(description = "结构化定位信息 JSON") private String locatorJson;
}
