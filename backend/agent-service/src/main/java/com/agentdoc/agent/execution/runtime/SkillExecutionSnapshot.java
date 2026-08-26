package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.service.SkillPackageEntry;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Skill执行快照
 * <p>
 * Agent任务执行前预构建的不可变快照记录，固化本次任务生效的全部Skill相关信息；
 * 避免Agent运行过程中，数据库Skill/SkillVersion配置变更干扰正在执行的任务。
 * 包含多个绑定Skill快照、Skill维度过滤放行MCP工具、快照校验信息、Skill提示片段。
 * </p>
 *
 * @param skills               当前Agent绑定生效的Skill快照列表
 * @param allowedMcpTools      Skill维度合并过滤后允许使用的MCP底层工具名称列表
 * @param skillSnapshotJson    Skill完整快照JSON字符串，用于缓存/调试/版本比对
 * @param skillInstructionHash Skill指令文本哈希，用于快速判断Skill提示内容是否发生变更
 * @param promptSection        Skill生成的提示词片段，会合并进入Agent最终systemPrompt
 */
public record SkillExecutionSnapshot(
        List<BoundSkillSnapshot> skills,
        List<String> readableResourcePaths,
        List<String> allowedMcpTools,
        String skillSnapshotJson,
        String skillInstructionHash,
        String promptSection) {

    /**
     * 单个绑定Skill执行快照
     * <p>固化某一个被Agent绑定的Skill指定版本信息，资源、指令、工具权限全部快照化。</p>
     *
     * @param skillId          Skill主表ID
     * @param skillVersionId   Skill版本ID
     * @param versionNo        Skill版本号
     * @param name             Skill名称
     * @param sha256           Skill压缩包sha256摘要(sha‑256)
     * @param storageKey       对象存储中Skill包存储key
     * @param instructionText  该Skill的指令文本，用于拼入系统提示词
     * @param allowedTools     该Skill自身声明允许使用的MCP工具名列表（来自allowedToolsJson）
     * @param readableResources 该Skill版本标记runtimeReadable=true的资源条目集合
     */
    public record BoundSkillSnapshot(
            Long skillId,
            Long skillVersionId,
            Integer versionNo,
            String name,
            String sha256,
            String storageKey,
            String instructionText,
            List<String> allowedTools,
            List<SkillPackageEntry> readableResources) {
    }
}
