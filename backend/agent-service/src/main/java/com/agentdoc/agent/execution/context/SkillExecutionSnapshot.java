package com.agentdoc.agent.execution.context;

import com.agentdoc.agent.execution.skill.SkillCandidate;

import java.util.List;

/**
 * Skill执行快照
 * <p>
 * Agent任务执行前预构建的不可变快照记录，固化本次任务生效的全部Skill相关信息；
 * 避免Agent运行过程中，数据库Skill/SkillVersion配置变更干扰正在执行的任务。
 * 包含多个绑定Skill快照、Skill维度过滤放行MCP工具、快照校验信息、Skill提示片段。
 * </p>
 *
 * @param boundSkills             当前 Agent 绑定生效的 Skill 快照列表
 * @param selectedSkillVersionIds 本次执行实际选择的 Skill 版本 ID
 * @param readableResourcePaths   已选 Skill 的可读资源路径
 * @param allowedMcpTools         Skill 维度过滤后的模型工具名称
 * @param skillSnapshotJson       Skill 完整快照 JSON
 * @param skillInstructionHash    Skill 指令文本哈希
 * @param catalogPromptSection    注入系统提示词的轻量 Skill 目录
 * @param selectionMode           本次执行实际使用的 Skill 选择模式
 * @param routerSnapshotJson      Router 调用快照；未调用时为空
 */
public record SkillExecutionSnapshot(
        List<SkillCandidate> boundSkills,
        List<Long> selectedSkillVersionIds,
        List<String> readableResourcePaths,
        List<String> allowedMcpTools,
        String skillSnapshotJson,
        String skillInstructionHash,
        String catalogPromptSection,
        String selectionMode,
        String routerSnapshotJson) {
    public SkillExecutionSnapshot {
        boundSkills = List.copyOf(boundSkills);
        selectedSkillVersionIds = List.copyOf(selectedSkillVersionIds);
        readableResourcePaths = List.copyOf(readableResourcePaths);
        allowedMcpTools = List.copyOf(allowedMcpTools);
    }
}
