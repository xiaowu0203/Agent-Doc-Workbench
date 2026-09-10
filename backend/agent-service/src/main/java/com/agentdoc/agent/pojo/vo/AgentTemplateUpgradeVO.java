package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Agent 模板升级预览或结果")
public record AgentTemplateUpgradeVO(
        @Schema(description = "空间 Agent ID") Long agentId,
        @Schema(description = "当前模板版本 ID") Long currentTemplateVersionId,
        @Schema(description = "目标模板版本 ID") Long targetTemplateVersionId,
        @Schema(description = "需要管理员裁决的冲突字段") List<String> conflictingFields,
        @Schema(description = "三方合并后的建议配置") AgentUpdateDTO proposedConfig,
        @Schema(description = "三方合并后的建议 Skill 版本 ID") List<Long> proposedSkillVersionIds,
        @Schema(description = "是否已应用升级") boolean applied) { }
