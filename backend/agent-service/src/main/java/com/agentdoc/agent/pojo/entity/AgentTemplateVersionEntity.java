package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_template_version")
@Schema(description = "Agent 模板不可变版本")
public class AgentTemplateVersionEntity extends BaseEntity {
    @Schema(description = "模板 ID") private Long templateId;
    @Schema(description = "版本号") private Integer versionNo;
    @Schema(description = "状态：0 草稿 / 1 已发布") private Integer status;
    @Schema(description = "默认展示名称") private String displayName;
    @Schema(description = "版本说明") private String description;
    @Schema(description = "系统提示词") private String systemPrompt;
    @Schema(description = "默认模型 ID") private Long modelId;
    @Schema(description = "Skill 选择模式") private String skillSelectionMode;
    @Schema(description = "Skill Router 模型 ID") private Long skillRouterModelId;
    @Schema(description = "是否启用外部 MCP") private Boolean externalMcpEnabled;
    @Schema(description = "Token 预算") private Long tokenBudget;
    @Schema(description = "工具白名单 JSON") private String toolWhitelist;
    @Schema(description = "最大迭代次数") private Integer maxIterations;
    @Schema(description = "执行超时秒数") private Integer executionTimeoutSeconds;
    @Schema(description = "创建人用户 ID") private Long createdBy;
    @Schema(description = "发布人用户 ID") private Long publishedBy;
    @Schema(description = "发布时间") private LocalDateTime publishedAt;
}
