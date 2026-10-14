package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_online_config")
@Schema(description = "AgentOnlineConfigEntity")
public class AgentOnlineConfigEntity extends BaseEntity {
    @Schema(description = "预分配实验身份") private Long experimentId;
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "当前 Agent") private Long agentId;
    @Schema(description = "BASELINE/CANDIDATE") private String role;
    @Schema(description = "创建意图摘要") private String requestHash;
    @Schema(description = "模板协议版本") private Integer schemaVersion;
    @Schema(description = "私有模板正文，无凭证") private String templateJson;
    @Schema(description = "模板摘要") private String templateHash;
    @Schema(description = "非提示词证明") private String nonPromptHash;
    @Schema(description = "完整依赖摘要") private String dependencyHash;
    @Schema(description = "执行超时") private Integer executionTimeoutSeconds;
    @Schema(description = "实际提示词上限") private Long maxSystemPromptBytes;
    @Schema(description = "捕获操作者") private Long createdBy;
}
