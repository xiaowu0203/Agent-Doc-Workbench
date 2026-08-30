package com.agentdoc.agent.execution.context;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;

import java.util.List;

/**
 * Agent运行时上下文记录
 * <p>
 * Agent执行期的完整固化上下文；任务开始执行前组装完毕，执行过程只读不修改。
 * 承载Agent配置、模型信息、任务入参、提示词、Skill执行快照、允许的MCP工具集合。
 * </p>
 *
 * @param executionId           本次 Agent 执行记录 ID
 * @param agent                 Agent 基础配置快照
 * @param model                 模型配置快照
 * @param taskInput             本次任务的业务输入
 * @param instruction           用户侧任务指令
 * @param systemPrompt          已组合完成的系统提示词
 * @param skillSnapshot         Skill 执行快照
 * @param allowedMcpTools       本次执行允许调用的模型工具名称
 * @param externalMcpConnections 本次执行冻结的外部 MCP 连接配置
 */
public record AgentRuntimeContext(
        Long executionId,
        AgentEntity agent,
        ModelEntity model,
        AgentTaskInputDTO taskInput,
        String instruction,
        String systemPrompt,
        SkillExecutionSnapshot skillSnapshot,
        List<String> allowedMcpTools,
        List<ExternalMcpConnection> externalMcpConnections) {

    public AgentRuntimeContext {
        agent = ExecutionSnapshotCopies.agent(agent);
        model = ExecutionSnapshotCopies.model(model);
        allowedMcpTools = List.copyOf(allowedMcpTools);
        externalMcpConnections = List.copyOf(externalMcpConnections);
    }

    @Override
    public AgentEntity agent() {
        return ExecutionSnapshotCopies.agent(agent);
    }

    @Override
    public ModelEntity model() {
        return ExecutionSnapshotCopies.model(model);
    }
}
