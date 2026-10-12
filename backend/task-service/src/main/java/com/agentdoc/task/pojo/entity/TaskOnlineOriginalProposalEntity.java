package com.agentdoc.task.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/** 与原始提案提交原子保存，不接受审批内容回填。 */
@Data
@TableName("task_online_original_proposal")
public class TaskOnlineOriginalProposalEntity {
    @TableId @Schema(description = "原始提案身份") private Long id;
    @Schema(description = "来源任务") private Long taskId;
    @Schema(description = "来源空间") private Long spaceId;
    @Schema(description = "来源 Agent") private Long agentId;
    @Schema(description = "来源实验") private Long experimentId;
    @Schema(description = "来源分配") private Long assignmentId;
    @Schema(description = "原文档") private Long documentId;
    @Schema(description = "提案原始基线") private Long baseVersion;
    @Schema(description = "冻结输入正文摘要") private String inputHash;
    @Schema(description = "来源绑定摘要") private String bindingHash;
    @Schema(description = "原始提案载荷摘要") private String payloadHash;
    @Schema(description = "捕获身份摘要") private String identityHash;
    @Schema(description = "审批前原始变更集合") private String originalChanges;
    @Schema(description = "原始捕获时间") private LocalDateTime capturedAt;
}
