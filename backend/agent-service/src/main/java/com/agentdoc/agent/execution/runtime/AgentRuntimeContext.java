package com.agentdoc.agent.execution.runtime;

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
 * @param agent Agent数据库实体，Agent基础配置
 * @param model 使用的大模型实体
 * @param taskInput 本次任务的业务输入DTO
 * @param instruction 用户侧任务指令
 * @param systemPrompt 系统提示词（已合并Agent配置、Skill相关提示）
 * @param skillSnapshot Skill执行快照，包含本次启用Skill的版本、资源、工具信息
 * @param allowedMcpTools 本次执行允许调用的MCP底层工具名称列表，运行时做工具调用权限校验
 */
public record AgentRuntimeContext(
        AgentEntity agent,
        ModelEntity model,
        AgentTaskInputDTO taskInput,
        String instruction,
        String systemPrompt,
        SkillExecutionSnapshot skillSnapshot,
        List<String> allowedMcpTools) {
}
