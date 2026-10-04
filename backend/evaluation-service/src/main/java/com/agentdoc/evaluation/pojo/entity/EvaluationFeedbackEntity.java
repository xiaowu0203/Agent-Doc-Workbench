package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_feedback")
@Schema(description = "不可变人工评估反馈")
public class EvaluationFeedbackEntity extends BaseEntity {
    @Schema(description = "线上分配身份，历史反馈为空") private Long onlineAssignmentId;
    @Schema(description = "原始产物证据身份") private Long originalEvidenceId;
    @Schema(description = "原始产物摘要") private String originalEvidenceHash;
    @Schema(description = "原始 rubric 0～4，既有 score 仍为0～1") private Integer rubricScore;
    @Schema(description = "BLINDED/NON_BLINDED/UNKNOWN") private String blindness;
    @Schema(description = "追加更正的前序反馈身份") private Long supersedesFeedbackId;
    @Schema(description = "理由码列表 JSON") private String reasonCodesJson;
    @Schema(description = "独立确认的严重安全事件") private Boolean seriousSafetyEvent;
    @Schema(description = "严重事件依据") private String safetyReason;
    @Schema(description = "归属空间ID")
    private Long spaceId;

    @Schema(description = "评估运行ID，可选")
    private Long runId;

    @Schema(description = "评估用例运行ID，可选")
    private Long caseRunId;

    @Schema(description = "工作台原始任务ID，可选")
    private Long taskId;

    @Schema(description = "Agent执行记录ID，可选")
    private Long executionId;

    @Schema(description = "反馈来源类型")
    private String sourceType;

    @Schema(description = "来源业务主键")
    private String sourceBusinessId;

    @Schema(description = "来源内容哈希，用于防重复提交")
    private String sourceHash;

    @Schema(description = "反馈标签，对应枚举 EvaluationFeedbackLabel")
    private String label;

    @Schema(description = "人工打分，取值区间 0.0 ~ 1.0")
    private BigDecimal score;

    @Schema(description = "反馈文字备注")
    private String comment;

    @Schema(description = "结构化定位信息 JSON")
    private String factsJson;

    @Schema(description = "创建人ID")
    private Long createdBy;
}
