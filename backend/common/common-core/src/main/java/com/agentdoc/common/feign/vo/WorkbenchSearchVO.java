package com.agentdoc.common.feign.vo;

/**
 * 工作台全局搜索分组结果。
 *
 * @param documents 文档结果
 * @param tasks 任务结果
 * @param agents Agent 结果
 */
public record WorkbenchSearchVO(
        WorkbenchSearchGroupVO documents,
        WorkbenchSearchGroupVO tasks,
        WorkbenchSearchGroupVO agents
) {
}
