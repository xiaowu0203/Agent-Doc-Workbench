package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.execution.context.SkillExecutionSnapshot;
import com.agentdoc.agent.execution.skill.SkillSelectionContext;
import com.agentdoc.agent.execution.skill.SkillCandidate;
import com.agentdoc.agent.execution.skill.SkillSelectionResult;
import com.agentdoc.agent.execution.skill.SkillSelectionStrategyRegistry;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.observability.AgentTelemetry;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.service.SkillSnapshotService;
import com.agentdoc.agent.service.AgentCandidateConfigService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.agentdoc.common.enums.TaskExecutionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final AgentTelemetry telemetry;
    private final AgentExecutionMapper executionMapper;
    private final AgentCandidateConfigService candidateConfigService;

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
        // 若任务执行类型是【ISOLATED】类型，则走prepareReplay
        if (TaskExecutionMode.ISOLATED.name().equals(input.executionMode())) {
            if (input.candidateConfigId() != null) {
                return prepareExperiment(a2aTaskId, a2aContextId, input, instruction);
            }
            return prepareReplay(a2aTaskId, a2aContextId, input, instruction);
        }
        // 读取并捕获Agent、模型、绑定技能、MCP连接等配置快照
        ExecutionPreparationTransactionService.CapturedExecution captured =
                transactionService.capture(input.agentId());
        // 获取Agent信息
        AgentEntity agent = captured.agent();
        // 获取模型信息
        ModelEntity model = captured.model();

        // 根据Agent配置的【Skill加载模式】获取到对应的【执行器】
        SkillSelectionResult selection = telemetry.selectSkills(agent.getSkillSelectionMode(),
                captured.boundSkills().size(), () ->
                        skillSelectionStrategyRegistry.require(agent.getSkillSelectionMode())
                                // 构建最终要传给大模型的【Skill】结果
                                .select(new SkillSelectionContext(
                                        instruction, agent, model, captured.boundSkills())));

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
        execution.setExecutionSnapshotSchemaVersion(AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION);
        execution.setExecutionSnapshotJson(AgentExecutionConvertor.snapshotJson(execution));
        execution.setExecutionSnapshotHash(AgentExecutionConvertor.snapshotHash(execution));
        // 写入数据库，状态为已提交(submitted)，代表前置准备完成，等待Runtime调度执行
        executionPersistenceService.insertSubmitted(execution);
        return new PreparedExecution(agent, model, snapshot, systemPrompt, execution,
                captured.externalMcpConnections());
    }

    /**
     * 准备一次Agent任务回放（Replay）执行实例
     * <p>
     * 基于源执行记录的冻结快照，校验快照完整性、哈希一致性、指令一致性；
     * 校验通过后从快照恢复Agent、模型、Skill快照信息，生成新的执行记录并持久化入库。
     * 当前回放隔离策略：禁止包含外部MCP依赖的快照回放。
     * </p>
     * @param a2aTaskId A2A协议任务ID
     * @param a2aContextId A2A上下文ID
     * @param input 回放任务入参DTO，携带源执行身份信息
     * @param instruction 用户指令，必须和源快照冻结指令完全一致
     * @return PreparedExecution 已完成校验与恢复的回放执行上下文
     * @throws BusinessException 快照身份、哈希、指令、外部MCP、快照解析等校验失败抛出CONFLICT冲突异常
     */
    private PreparedExecution prepareReplay(String a2aTaskId, String a2aContextId,
                                            AgentTaskInputDTO input, String instruction) {
        // 校验回放来源身份字段是否齐全
        requireReplayIdentity(input);
        // 查询原始源执行记录
        AgentExecutionEntity source = executionMapper.selectById(input.sourceExecutionId());
        // 多重一致性校验：源记录存在性、任务/空间匹配、快照版本、快照哈希校验
        if (source == null || !input.sourceTaskId().equals(source.getWorkbenchTaskId())
                || !input.spaceId().equals(source.getSpaceId())
                || source.getExecutionSnapshotSchemaVersion() == null
                || source.getExecutionSnapshotSchemaVersion() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || source.getExecutionSnapshotJson() == null
                // 入参携带的快照哈希与数据库存储哈希比对
                || !input.sourceExecutionSnapshotHash().equals(source.getExecutionSnapshotHash())
                // 二次校验：数据库快照JSON重新规范化计算哈希，防篡改
                || !input.sourceExecutionSnapshotHash().equals(
                SnapshotCanonicalV3Utils.hashEnvelope(source.getExecutionSnapshotJson()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源执行快照身份无效");
        }
        // 校验用户指令：回放指令必须与源快照冻结指令完全一致，指令不可变更
        if (!Objects.equals(source.getUserInstructionSnapshot(), instruction)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 指令与来源冻结输入不一致");
        }
        // 解析快照外层envelope包装节点
        JsonNode envelope = JsonUtils.parse(source.getExecutionSnapshotJson(), JsonNode.class);
        // 提取业务snapshot主体节点
        JsonNode snapshot = envelope == null ? null : envelope.get("snapshot");
        // 校验外层envelope结构、schema版本、snapshot节点合法性
        if (envelope == null || envelope.path("schemaVersion").asInt(-1)
                != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION || snapshot == null || !snapshot.isObject()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源执行快照无法解析");
        }
        // 获取外部MCP快照节点
        JsonNode externalMcp = snapshot.get("externalMcpSnapshot");
        // 回放隔离策略：快照中存在非空外部MCP数组，拒绝回放
        if (externalMcp != null && !externalMcp.isNull()
                && (!externalMcp.isArray() || !externalMcp.isEmpty())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源包含外部 MCP，当前隔离策略拒绝执行");
        }

        return prepareFrozenExecution(a2aTaskId, a2aContextId, input, instruction, snapshot,
                source.getPromptHash(), input.sourceExecutionSnapshotHash(), "Replay");
    }

    private PreparedExecution prepareExperiment(String a2aTaskId, String a2aContextId,
                                                AgentTaskInputDTO input, String instruction) {
        requireReplayIdentity(input);
        if (input.candidateConfigId() == null || input.candidateSnapshotSchemaVersion() == null
                || input.candidateSnapshotSchemaVersion() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || input.candidateSnapshotHash() == null || input.candidateSnapshotHash().length() != 64) {
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 候选配置身份不完整");
        }
        AgentExecutionEntity source = executionMapper.selectById(input.sourceExecutionId());
        if (source == null || !Objects.equals(input.sourceTaskId(), source.getWorkbenchTaskId())
                || !Objects.equals(input.spaceId(), source.getSpaceId())
                || !Objects.equals(input.agentId(), source.getAgentId())
                || !Objects.equals(input.sourceExecutionSnapshotSchemaVersion(),
                source.getExecutionSnapshotSchemaVersion())
                || !Objects.equals(input.sourceExecutionSnapshotHash(), source.getExecutionSnapshotHash())
                || !Objects.equals(input.sourceExecutionSnapshotHash(),
                SnapshotCanonicalV3Utils.hashEnvelope(source.getExecutionSnapshotJson()))
                || !Objects.equals(source.getUserInstructionSnapshot(), instruction)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 来源执行身份无效");
        }
        AgentCandidateConfigService.RestoredCandidateConfig candidate = candidateConfigService.restore(
                input.candidateConfigId(), input.spaceId(), input.candidateSnapshotHash());
        if (!Objects.equals(input.agentId(), candidate.agentId())
                || !Objects.equals(input.sourceExecutionSnapshotHash(), candidate.sourceSnapshotHash())
                || !Objects.equals(input.candidateSnapshotSchemaVersion(),
                candidate.candidateSnapshotSchemaVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 候选配置与来源执行不一致");
        }
        return prepareFrozenExecution(a2aTaskId, a2aContextId, input, instruction,
                candidate.snapshot(), promptService.hash(candidate.systemPrompt(), instruction),
                candidate.candidateSnapshotHash(), "Experiment");
    }

    private PreparedExecution prepareFrozenExecution(String a2aTaskId, String a2aContextId,
                                                      AgentTaskInputDTO input, String instruction,
                                                      JsonNode snapshot, String promptHash,
                                                      String expectedSnapshotHash, String label) {
        JsonNode externalMcp = snapshot.get("externalMcpSnapshot");
        if (externalMcp != null && !externalMcp.isNull()
                && (!externalMcp.isArray() || !externalMcp.isEmpty())) {
            throw new BusinessException(ErrorCode.CONFLICT, label + " 快照包含外部 MCP");
        }
        // 从快照恢复Agent实体
        AgentEntity agent = restoreAgent(snapshot, input);
        // 从快照恢复模型实体
        ModelEntity model = restoreModel(snapshot.path("model"));
        // 回填模型配置版本
        model.setConfigVersion(longValue(snapshot.get("modelConfigVersion")));
        // 绑定Agent与模型ID
        agent.setModelId(model.getId());
        // 恢复Skill执行快照
        SkillExecutionSnapshot skillSnapshot = restoreSkillSnapshot(snapshot);
        // 读取冻结的系统提示词
        String systemPrompt = text(snapshot, "systemPrompt");
        if (systemPrompt == null) {
            throw new BusinessException(ErrorCode.CONFLICT, label + " 快照缺少冻结系统提示词");
        }

        // 将回放上下文转换为数据库执行实体
        AgentExecutionEntity execution = AgentExecutionConvertor.toEntity(
                a2aTaskId, a2aContextId, input, agent, model, systemPrompt, promptHash);
        // 回填冻结用户指令快照
        execution.setUserInstructionSnapshot(instruction);
        // Skill绑定快照JSON
        execution.setSkillSnapshotJson(JsonUtils.toJson(skillSnapshot.boundSkills()));
        // Skill指令哈希
        execution.setSkillInstructionHash(skillSnapshot.skillInstructionHash());
        // 技能选择模式
        execution.setSkillSelectionMode(text(snapshot, "skillSelectionMode"));
        // 生效的技能选择模式
        execution.setSkillSelectionEffectiveMode(skillSnapshot.selectionMode());
        // 技能路由模型ID
        execution.setSkillRouterModelId(longValue(snapshot.get("skillRouterModelId")));
        // 选中的技能版本ID列表JSON
        execution.setSelectedSkillVersionIdsJson(JsonUtils.toJson(skillSnapshot.selectedSkillVersionIds()));
        // 技能路由快照
        execution.setSkillRouterSnapshotJson(skillSnapshot.routerSnapshotJson());
        // 工具白名单快照
        execution.setToolWhitelistSnapshot(skillSnapshot.allowedMcpTools() == null
                ? null : JsonUtils.toJson(skillSnapshot.allowedMcpTools()));
        // 工具定义快照
        JsonNode toolDefinitions = snapshot.get("toolDefinitions");
        execution.setToolDefinitionSnapshotJson(toolDefinitions == null || toolDefinitions.isNull()
                ? null : JsonUtils.toJson(toolDefinitions));
        // 外部MCP快照JSON
        execution.setExternalMcpSnapshotJson(externalMcp == null || externalMcp.isNull()
                ? null : JsonUtils.toJson(externalMcp));
        // 快照协议版本固定为V3
        execution.setExecutionSnapshotSchemaVersion(AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION);
        // 生成新的执行快照JSON
        execution.setExecutionSnapshotJson(AgentExecutionConvertor.snapshotJson(execution));
        // 计算新快照哈希
        execution.setExecutionSnapshotHash(AgentExecutionConvertor.snapshotHash(execution));
        // 最终校验：恢复生成的快照哈希必须与源快照哈希保持一致，保证回放状态完全等价
        if (!expectedSnapshotHash.equals(execution.getExecutionSnapshotHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, label + " 恢复后的执行快照与冻结配置不一致");
        }
        // 持久化提交状态的回放执行记录
        executionPersistenceService.insertSubmitted(execution);
        // 封装回放执行上下文返回
        return new PreparedExecution(agent, model, skillSnapshot, systemPrompt, execution, List.of());
    }

    /**
     * 校验回放来源身份信息完整性
     * <p>
     * 校验源任务ID、源执行ID、快照版本、快照哈希（固定64位SHA256）必填。
     * </p>
     * @param input 回放任务入参DTO
     * @throws BusinessException 身份字段缺失或不合法抛出CONFLICT
     */
    private void requireReplayIdentity(AgentTaskInputDTO input) {
        if (input.sourceTaskId() == null || input.sourceExecutionId() == null
                || input.sourceExecutionSnapshotSchemaVersion() == null
                || input.sourceExecutionSnapshotSchemaVersion() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || input.sourceExecutionSnapshotHash() == null
                || input.sourceExecutionSnapshotHash().length() != 64) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源执行身份不完整");
        }
    }

    /**
     * 从快照JsonNode恢复Agent实体
     * @param snapshot 执行快照根节点
     * @param input 回放入参DTO
     * @return 填充完成的AgentEntity
     * @throws BusinessException 快照缺失Agent标识时抛出CONFLICT
     */
    private AgentEntity restoreAgent(JsonNode snapshot, AgentTaskInputDTO input) {
        AgentEntity agent = new AgentEntity();
        // 源AgentID
        agent.setId(longValue(snapshot.get("sourceAgentId")));
        // 空间ID取自入参
        agent.setSpaceId(input.spaceId());
        // Agent名称快照
        agent.setName(text(snapshot, "agentNameSnapshot"));
        // Agent配置版本
        agent.setConfigVersion(longValue(snapshot.get("agentConfigVersion")));
        // 最大迭代次数
        agent.setMaxIterations(intValue(snapshot.get("maxIterations")));
        // 执行超时秒数
        agent.setExecutionTimeoutSeconds(intValue(snapshot.get("executionTimeoutSeconds")));
        // 系统提示词
        agent.setSystemPrompt(text(snapshot, "systemPrompt"));
        // Token预算取自入参
        agent.setTokenBudget(input.tokenBudget());
        // 技能选择模式
        agent.setSkillSelectionMode(text(snapshot, "skillSelectionMode"));
        // 技能路由模型ID
        agent.setSkillRouterModelId(longValue(snapshot.get("skillRouterModelId")));
        // 回放强制关闭外部MCP
        agent.setExternalMcpEnabled(Boolean.FALSE);
        // 工具白名单
        JsonNode whitelist = snapshot.get("toolWhitelist");
        agent.setToolWhitelist(whitelist == null || whitelist.isNull() ? null : JsonUtils.toJson(whitelist));
        // AgentID不能为空
        if (agent.getId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源缺少 Agent 快照");
        }
        return agent;
    }

    /**
     * 从快照节点恢复模型实体，获取回放使用的模型凭证
     * @param modelSnapshot 模型快照JsonNode
     * @return 填充完成的ModelEntity
     * @throws BusinessException 模型快照缺失或不合法抛出CONFLICT
     */
    private ModelEntity restoreModel(JsonNode modelSnapshot) {
        if (modelSnapshot == null || !modelSnapshot.isObject()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源缺少模型快照");
        }
        Long modelId = longValue(modelSnapshot.get("id"));
        if (modelId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 来源缺少模型快照");
        }
        // 解析回放模型凭证（密钥等敏感信息）
        ModelEntity model = transactionService.resolveReplayModelCredential(modelId);
        model.setProvider(text(modelSnapshot, "provider"));
        model.setAdapterType(text(modelSnapshot, "adapterType"));
        model.setModelKey(text(modelSnapshot, "modelKey"));
        model.setDisplayName(text(modelSnapshot, "displayName"));
        model.setBaseUrl(text(modelSnapshot, "baseUrl"));
        model.setOptionsJson(text(modelSnapshot, "optionsJson"));
        model.setContextWindow(longValue(modelSnapshot.get("contextWindow")));
        model.setMaxOutputTokens(longValue(modelSnapshot.get("maxOutputTokens")));
        model.setInputPricePerMillion(decimalValue(modelSnapshot.get("inputPricePerMillion")));
        model.setOutputPricePerMillion(decimalValue(modelSnapshot.get("outputPricePerMillion")));
        return model;
    }

    /**
     * 从快照恢复Skill执行快照对象，校验选中技能与候选技能的引用一致性
     * @param snapshot 执行快照根节点
     * @return SkillExecutionSnapshot 技能执行快照
     * @throws BusinessException 技能快照引用关系不合法抛出CONFLICT
     */
    private SkillExecutionSnapshot restoreSkillSnapshot(JsonNode snapshot) {
        // 解析候选Skill列表
        JsonNode skillsNode = snapshot.get("skillSnapshot");
        List<SkillCandidate> skills = skillsNode == null || skillsNode.isNull()
                ? List.of() : JsonUtils.parse(skillsNode.toString(),
                new TypeReference<List<SkillCandidate>>() { });
        // 解析本次选中的技能版本ID
        JsonNode selectedNode = snapshot.get("selectedSkillVersionIds");
        List<Long> selected = selectedNode == null || selectedNode.isNull() ? List.of()
                : JsonUtils.parse(selectedNode.toString(), new TypeReference<List<Long>>() { });
        // 解析工具白名单
        JsonNode whitelistNode = snapshot.get("toolWhitelist");
        List<String> whitelist = whitelistNode == null || whitelistNode.isNull() ? null
                : JsonUtils.parse(whitelistNode.toString(), new TypeReference<List<String>>() { });
        // 校验：选中的每个技能版本ID，必须存在于候选Skill列表中，防止引用断裂
        if (skills == null || selected == null || selected.stream().anyMatch(id -> skills.stream()
                .noneMatch(skill -> skill.skillVersionId().equals(id)))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay Skill 快照无效");
        }
        // 提取选中技能对应的可读资源路径，去重并排序
        List<String> readablePaths = skills.stream().filter(skill -> selected.contains(skill.skillVersionId()))
                .flatMap(skill -> skill.readableResources().stream().map(value -> value.path()))
                .distinct().sorted().toList();
        // 技能路由快照
        JsonNode routerNode = snapshot.get("skillRouterSnapshot");
        return new SkillExecutionSnapshot(skills, selected, readablePaths, whitelist,
                JsonUtils.toJson(skills), text(snapshot, "skillInstructionHash"), "",
                text(snapshot, "skillSelectionEffectiveMode"),
                routerNode == null || routerNode.isNull() ? null : JsonUtils.toJson(routerNode));
    }

    /**
     * 读取JsonNode文本字段，null/空节点返回null
     * @param node Json节点
     * @param field 字段名
     * @return 字段文本值
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 读取JsonNode长整型，无法转为long返回null
     * @param value Json节点
     * @return Long值
     */
    private Long longValue(JsonNode value) {
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.longValue();
    }

    /**
     * 读取JsonNode整型，无法转为int返回null
     * @param value Json节点
     * @return Integer值
     */
    private Integer intValue(JsonNode value) {
        return value == null || value.isNull() || !value.canConvertToInt() ? null : value.intValue();
    }

    /**
     * 读取JsonNode并转为BigDecimal，用于价格数值；解析失败抛业务异常
     * @param value Json节点
     * @return BigDecimal数值
     * @throws BusinessException 数字格式非法抛出CONFLICT
     */
    private BigDecimal decimalValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 模型价格快照无效");
        }
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
            value.put("authParamName", connection.authParamName());
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
