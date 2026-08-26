package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.execution.runtime.SkillExecutionSnapshot;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Agent任务执行前置准备服务
 * <p>
 * 在任务正式执行开始前一次性锁定 Agent 配置并生成 Skill/Prompt 快照。
 * 通过数据库行锁锁定Agent记录，避免准备阶段Agent配置被并发修改；
 * 完成模型校验、技能快照生成、系统提示词组装、长度校验，
 * 生成执行实体并持久化【已提交】状态，返回准备完成的上下文对象供后续Runtime使用。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ExecutionPreparationService {

    private final AgentMapper agentMapper;
    private final AgentExecutionPersistenceService executionPersistenceService;
    private final ModelService modelService;
    private final SkillSnapshotService skillSnapshotService;
    private final PromptService promptService;
    private final SkillPackageProperties skillPackageProperties;

    /**
     * 执行Agent任务前置准备全流程
     * <p>流程：行锁查询Agent → 状态校验 → 获取可用模型 → 生成技能快照 → 组装系统提示词
     * → 校验提示词大小上限 → 构造执行实体并写入数据库(submitted状态) → 返回准备完成上下文。
     * 整个过程在事务内执行，保证Agent配置读取一致性。</p>
     *
     * @param a2aTaskId     A2A协议任务唯一ID
     * @param a2aContextId  A2A会话上下文ID
     * @param input         Agent任务入参DTO，携带agentId等任务基础信息
     * @param instruction   用户输入指令
     * @return {@link PreparedExecution} 准备完成的执行上下文，包含Agent、模型、快照、提示词、执行实体
     * @throws BusinessException Agent不存在、Agent被禁用、模型不可用、系统提示词超限均抛出业务异常
     */
    @Transactional
    public PreparedExecution prepare(String a2aTaskId, String a2aContextId, AgentTaskInputDTO input,
                                     String instruction) {
        // 获取AgentId
        Long agentId = input.agentId();

        // FOR UPDATE行锁锁定Agent，防止准备过程中Agent配置被并发更新
        AgentEntity agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getId, agentId).last("FOR UPDATE"));

        // 校验Agent是否存在、状态是否可用
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        if (!AgentStatus.ENABLED.matches(agent.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已禁用");
        }

        // 校验并获取已启用的大模型配置
        ModelEntity model = modelService.requireEnabled(agent.getModelId());

        // 生成本次任务隔离的Skill执行快照
        SkillExecutionSnapshot snapshot = skillSnapshotService.snapshot(agent);

        // 拼接最终系统提示词，注入技能快照片段
        String systemPrompt = promptService.systemPrompt(agent.getSystemPrompt(), snapshot.promptSection());

        // 校验最终系统提示词字节大小，不超过配置上限
        if (systemPrompt.getBytes(StandardCharsets.UTF_8).length
                > skillPackageProperties.getMaxSystemPromptSize().toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "最终系统提示词超过限制");
        }

        // 构建Agent执行记录实体，计算提示词+指令哈希用于缓存/溯源
        AgentExecutionEntity execution = AgentExecutionConvertor.toEntity(
                a2aTaskId, a2aContextId, input, agent, model, systemPrompt,
                promptService.hash(systemPrompt, instruction));

        // 持久化技能快照、指令哈希、MCP工具白名单快照，保证任务重放使用同一套快照
        execution.setSkillSnapshotJson(snapshot.skillSnapshotJson());
        execution.setSkillInstructionHash(snapshot.skillInstructionHash());
        execution.setToolWhitelistSnapshot(JsonUtils.toJson(snapshot.allowedMcpTools()));
        // 写入数据库，状态为已提交(submitted)
        executionPersistenceService.insertSubmitted(execution);
        return new PreparedExecution(agent, model, snapshot, systemPrompt, execution);
    }

    /**
     * 执行准备完成结果记录
     * <p>承载Runtime执行阶段所需要的全部前置上下文：Agent配置、模型、技能快照、最终系统提示词、执行数据库实体。</p>
     *
     * @param agent          锁定后的Agent数据库实体
     * @param model          本次任务使用的模型配置
     * @param skillSnapshot  本次任务隔离的技能快照，包含工具白名单、提示片段、快照JSON
     * @param systemPrompt   组装完成后的最终系统提示词
     * @param execution      已入库的Agent执行记录实体
     */
    public record PreparedExecution(AgentEntity agent, ModelEntity model,
                                    SkillExecutionSnapshot skillSnapshot, String systemPrompt,
                                    AgentExecutionEntity execution) {
    }
}
