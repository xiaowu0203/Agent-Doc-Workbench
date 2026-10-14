package com.agentdoc.agent.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_online_original_text")
@Schema(description = "线上最终文本不可变证据")
public class AgentOnlineOriginalTextEntity {
    @TableId @Schema(description = "Execution ID，兼作证据身份") private Long id;
    @Schema(description = "原 Task 身份") private Long taskId;
    @Schema(description = "空间身份") private Long spaceId;
    @Schema(description = "Agent 身份") private Long agentId;
    @Schema(description = "实验身份") private Long experimentId;
    @Schema(description = "分配身份") private Long assignmentId;
    @Schema(description = "冻结 binding 摘要") private String bindingHash;
    @Schema(description = "原始正文摘要") private String contentHash;
    @Schema(description = "证据身份摘要") private String identityHash;
    @Schema(description = "完整最终文本，仅受控证据读取") private String originalText;
    @Schema(description = "原始捕获时间") private LocalDateTime capturedAt;
}
