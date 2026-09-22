package com.agentdoc.common.feign.dto;

/**
 * Agent 任务输入DTO，承载A2A能力令牌校验所需的全部绑定上下文。
 * <p>
 * 所有字段会参与执行快照哈希计算与JWT Claim校验，请求参数必须与签发令牌时绑定的值完全一致，
 * 任意字段不一致都会触发FORBIDDEN拒绝访问，防止令牌被挪用到其他任务/文档/空间上下文。
 *
 * @param workbenchTaskId               工作台任务ID，本次任务的顶层任务标识
 * @param agentId                       Agent实例ID，绑定本次执行所属Agent
 * @param spaceId                       工作空间ID，权限隔离边界
 * @param documentId                    目标文档ID，限定操作文档范围
 * @param tokenBudget                   Token预算，本次执行允许消耗的Token配额上限
 * @param executionMode                 执行模式，取值为 TaskExecutionMode 的名称（LIVE / ISOLATED）
 * @param documentVersionSnapshot       文档版本快照号，绑定执行时的文档版本
 * @param documentContentSha256        文档内容SHA256哈希，校验文档内容未被篡改
 * @param inputSnapshotSchemaVersion    输入快照Schema版本，用于快照序列化兼容
 * @param inputSnapshotHash             输入上下文快照哈希，A2A令牌核心校验字段
 * @param sourceTaskId                  来源任务ID，链式执行溯源；无上游时可为null
 * @param sourceExecutionId             来源执行ID，链式执行溯源；无上游时可为null
 * @param sourceExecutionSnapshotSchemaVersion 上游执行快照Schema版本，溯源快照兼容
 * @param sourceExecutionSnapshotHash   上游执行快照哈希，校验上游上下文未篡改
 * @param mcpServerUrl                  工作台内置MCP服务地址
 * @param taskCapability                A2A能力令牌（task capability jwt），用于权限校验
 */
public record AgentTaskInputDTO(
        Long workbenchTaskId,
        Long agentId,
        Long spaceId,
        Long documentId,
        Long tokenBudget,
        String executionMode,
        Long documentVersionSnapshot,
        String documentContentSha256,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        Long sourceTaskId,
        Long sourceExecutionId,
        Integer sourceExecutionSnapshotSchemaVersion,
        String sourceExecutionSnapshotHash,
        String mcpServerUrl,
        String taskCapability) {
}
