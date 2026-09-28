package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_candidate_config")
@Schema(description = "Agent 不可变候选执行配置")
public class AgentCandidateConfigEntity extends BaseEntity {
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "来源 Agent ID") private Long agentId;
    @Schema(description = "来源 Task ID") private Long sourceTaskId;
    @Schema(description = "来源 AgentExecution ID") private Long sourceExecutionId;
    @Schema(description = "候选配置创建幂等键") private String requestKey;
    @Schema(description = "候选配置创建请求 hash") private String requestHash;
    @Schema(description = "来源执行快照 schema 版本") private Integer sourceSnapshotSchemaVersion;
    @Schema(description = "来源执行快照 hash") private String sourceSnapshotHash;
    @Schema(description = "候选执行快照 schema 版本") private Integer candidateSnapshotSchemaVersion;
    @Schema(description = "候选执行快照 hash") private String candidateSnapshotHash;
    @Schema(description = "移除 systemPrompt 后的公共快照 hash") private String snapshotWithoutPromptHash;
    @Schema(description = "实际 Prompt 差异字段路径 JSON") private String promptDiffFieldPaths;
    @Schema(description = "候选 Agent Prompt 正文") private String agentPrompt;
    @Schema(description = "统一构建后的最终 system prompt") private String systemPrompt;
    @Schema(description = "候选 Prompt 派生 hash") private String promptHash;
    @Schema(description = "候选 canonical execution snapshot v3") private String executionSnapshotJson;
    @Schema(description = "创建人") private Long createdBy;
}
