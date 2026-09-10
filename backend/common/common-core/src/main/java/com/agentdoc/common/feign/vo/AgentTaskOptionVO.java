package com.agentdoc.common.feign.vo;

/**
 * 新建任务页使用的 Agent 选项，不暴露系统提示词或原始文档范围配置。
 *
 * @param id Agent ID
 * @param name Agent 名称
 * @param modelDisplayName 模型展示名称
 * @param skillSelectionMode Skill 选择模式
 * @param tokenBudget Agent Token 预算上限
 * @param executionTimeoutSeconds 执行超时秒数
 * @param skillCount 启用的 Skill 绑定数
 * @param mcpCount 启用的 MCP 绑定数
 */
public record AgentTaskOptionVO(
        Long id,
        String name,
        String modelDisplayName,
        String skillSelectionMode,
        Long tokenBudget,
        Integer executionTimeoutSeconds,
        long skillCount,
        long mcpCount) {
}
