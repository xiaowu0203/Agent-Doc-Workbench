package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.execution.context.SkillExecutionSnapshot;
import com.agentdoc.agent.execution.skill.SkillSelectionContext;
import com.agentdoc.agent.execution.skill.SkillSelectionResult;
import com.agentdoc.agent.execution.skill.SkillSelectionStrategyRegistry;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.service.SkillSnapshotService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent任务执行前置准备服务
 * <p>
 * 在任务正式执行开始前冻结 Agent 配置并生成 Skill/Prompt 快照。
 * 通过短事务锁定并复制配置，释放行锁后执行 Skill 路由，再以短事务写入执行记录；
 * 完成模型校验、技能选择、系统提示词组装、长度校验，
 * 生成执行实体并持久化【已提交】状态，返回准备完成的上下文对象供后续Runtime使用。
 * </p>
 * <p>
 * 核心设计：Agent行锁仅存在于短事务内，路由、提示词组装等重逻辑放在事务外部，
 * 避免长事务占用数据库连接；任务执行全程基于本次生成的快照，不受后续Agent配置变更影响，支持任务重放。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ExecutionPreparationService {

    private final ExecutionPreparationTransactionService transactionService;
    private final AgentExecutionPersistenceService executionPersistenceService;
    private final SkillSnapshotService skillSnapshotService;
    private final SkillSelectionStrategyRegistry skillSelectionStrategyRegistry;
    private final PromptService promptService;
    private final SkillPackageProperties skillPackageProperties;

    /**
     * 执行Agent任务前置准备全流程
     * <p>流程：行锁查询Agent → 状态校验 → 获取可用模型 → 生成技能快照 → 组装系统提示词
     * → 校验提示词大小上限 → 构造执行实体并写入数据库(submitted状态) → 返回准备完成上下文。
     * Router 模型调用不处于事务内，也不持有 Agent 行锁。</p>
     *
     * @param a2aTaskId    A2A协议任务唯一ID
     * @param a2aContextId A2A会话上下文ID
     * @param input        Agent任务入参DTO，携带agentId等任务基础信息
     * @param instruction  用户输入指令
     * @return {@link PreparedExecution} 准备完成的执行上下文，包含Agent、模型、快照、提示词、执行实体
     * @throws BusinessException Agent不存在、Agent被禁用、模型不可用、系统提示词超限均抛出业务异常
     */
    public PreparedExecution prepare(String a2aTaskId, String a2aContextId, AgentTaskInputDTO input,
                                     String instruction) {
        // 读取并捕获Agent、模型、绑定技能、MCP连接等配置快照
        ExecutionPreparationTransactionService.CapturedExecution captured =
                transactionService.capture(input.agentId());
        // 获取Agent信息
        AgentEntity agent = captured.agent();
        // 获取模型信息
        ModelEntity model = captured.model();

        // 根据Agent配置的【Skill加载模式】获取到对应的【执行器】
        SkillSelectionResult selection = skillSelectionStrategyRegistry.require(agent.getSkillSelectionMode())
                // 构建最终要传给大模型的【Skill】结果
                .select(new SkillSelectionContext(instruction, agent, model, captured.boundSkills()));

        // 生成本次任务隔离的技能执行快照，固化本次要使用的技能集合、MCP工具白名单等，后续Agent配置变更不影响本次任务
        SkillExecutionSnapshot snapshot = skillSnapshotService.snapshot(
                // Agent信息
                agent,
                // 绑定的所有Skill（并不是经过路由之后的结果）
                captured.boundSkills(),
                // Skill 选择结果
                selection);

        // 拼接最终系统提示词，注入技能快照片段
        String systemPrompt = promptService.systemPrompt(agent.getSystemPrompt(), snapshot.catalogPromptSection());

        // 校验最终系统提示词字节大小，不超过配置上限
        if (systemPrompt.getBytes(StandardCharsets.UTF_8).length
                > skillPackageProperties.getMaxSystemPromptSize().toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "最终系统提示词超过限制");
        }

        // 构建Agent执行记录实体，计算提示词+指令哈希用于缓存/溯源
        AgentExecutionEntity execution = AgentExecutionConvertor.toEntity(
                a2aTaskId, a2aContextId, input, agent, model, systemPrompt,
                promptService.hash(systemPrompt, instruction));
        execution.setUserInstructionSnapshot(instruction);

        // 持久化技能快照、指令哈希、MCP工具白名单快照，保证任务重放使用同一套快照
        execution.setSkillSnapshotJson(snapshot.skillSnapshotJson());
        execution.setSkillInstructionHash(snapshot.skillInstructionHash());
        execution.setSkillSelectionMode(agent.getSkillSelectionMode());
        execution.setSkillSelectionEffectiveMode(snapshot.selectionMode());

        // Router模式下优先取Agent配置的路由模型，未配置则回退使用主模型
        execution.setSkillRouterModelId(SkillSelectionMode.ROUTER.name().equals(agent.getSkillSelectionMode())
                ? (agent.getSkillRouterModelId() == null ? model.getId() : agent.getSkillRouterModelId())
                : null);
        execution.setSelectedSkillVersionIdsJson(JsonUtils.toJson(snapshot.selectedSkillVersionIds()));
        execution.setSkillRouterSnapshotJson(snapshot.routerSnapshotJson());
        execution.setToolWhitelistSnapshot(JsonUtils.toJson(snapshot.allowedMcpTools()));
        execution.setExternalMcpSnapshotJson(externalMcpSnapshot(captured.externalMcpConnections()));
        // 写入数据库，状态为已提交(submitted)，代表前置准备完成，等待Runtime调度执行
        executionPersistenceService.insertSubmitted(execution);
        return new PreparedExecution(agent, model, snapshot, systemPrompt, execution,
                captured.externalMcpConnections());
    }

    /**
     * 将外部MCP连接集合生成可持久化快照JSON
     * <p>
     * 保存服务ID、key、配置版本、端点哈希、认证类型、工具白名单；
     * 对endpointUrl做SHA256摘要，避免原始敏感地址明文大量存储，用于比对配置是否发生变化。
     * </p>
     *
     * @param connections 本次捕获冻结的外部MCP连接集合
     * @return 序列化后的快照JSON字符串
     */
    private String externalMcpSnapshot(List<ExternalMcpConnection> connections) {
        return JsonUtils.toJson(connections.stream().map(connection -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("serverId", connection.serverId());
            value.put("serverKey", connection.serverKey());
            value.put("configVersion", connection.configVersion());
            value.put("endpointSha256", sha256(connection.endpointUrl()));
            value.put("authType", connection.authType());
            value.put("toolWhitelist", connection.bindingToolWhitelist());
            return value;
        }).toList());
    }

    /**
     * 对字符串做SHA‑256哈希，输出十六进制格式摘要
     *
     * @param value 待哈希原始字符串
     * @return SHA‑256 hex摘要字符串
     * @throws IllegalStateException JDK环境缺失SHA‑256算法实现时抛出
     */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(SkillConstant.SHA_256)
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 执行准备完成结果记录
     * <p>承载Runtime执行阶段所需要的全部前置上下文：Agent配置、模型、技能快照、最终系统提示词、执行数据库实体。
     * 全部为任务冻结后的快照数据，Runtime执行阶段直接使用，不再读取最新Agent数据库配置。</p>
     *
     * @param agent                 锁定后的Agent数据库实体（本次任务快照）
     * @param model                 本次任务使用的模型配置
     * @param skillSnapshot         本次任务隔离的技能快照，包含工具白名单、提示片段、快照JSON
     * @param systemPrompt          组装完成后的最终系统提示词
     * @param execution             已入库的Agent执行记录实体，状态为submitted
     * @param externalMcpConnections 本次执行冻结的外部 MCP 连接配置快照
     */
    public record PreparedExecution(AgentEntity agent, ModelEntity model,
                                    SkillExecutionSnapshot skillSnapshot, String systemPrompt,
                                    AgentExecutionEntity execution,
                                    List<ExternalMcpConnection> externalMcpConnections) {
    }
}
