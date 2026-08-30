package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.skill.archive.SkillPackageEntry;

import java.util.List;

/**
 * 执行准备阶段冻结的单个 Skill 版本候选。
 *
 * @param skillId              Skill ID
 * @param skillVersionId       Skill 版本 ID
 * @param versionNo            版本号
 * @param name                 Skill 技术名称
 * @param activationDescription 版本化激活描述
 * @param sha256               Skill 包摘要
 * @param storageKey           Skill 包对象存储键
 * @param instructionText      Skill 指令正文
 * @param allowedTools         Skill 允许使用的工具名称
 * @param readableResources    运行时可读资源清单
 */
public record SkillCandidate(
        Long skillId,
        Long skillVersionId,
        Integer versionNo,
        String name,
        String activationDescription,
        String sha256,
        String storageKey,
        String instructionText,
        List<String> allowedTools,
        List<SkillPackageEntry> readableResources) {
    public SkillCandidate {
        allowedTools = allowedTools == null ? null : List.copyOf(allowedTools);
        readableResources = List.copyOf(readableResources);
    }
}
