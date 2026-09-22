package com.agentdoc.agent.convertor;

import com.agentdoc.agent.enums.AgentExecutionStatus;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.enums.TokenValueSource;
import com.agentdoc.common.context.TraceContext;
import com.agentdoc.common.pojo.TokenValue;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.agentdoc.agent.constant.AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION;

/**
 * Agent执行记录转换器
 * <p>
 * 负责AgentExecutionEntity执行记录实体的构建与状态流转更新；
 * 任务从A2A下发开始，记录执行快照、时间、token消耗、结果、错误信息。
 * 包含：初始化实体、标记开始工作、完成、失败、取消、模型配置快照序列化。
 * </p>
 */
public final class AgentExecutionConvertor {

    private AgentExecutionConvertor() {
    }

    /**
     * 构建Agent执行记录实体（任务刚被提交，尚未开始推理）
     * <p>
     * A2A任务到达后初始化一条执行记录，保存A2A task/contextId、关联workbench任务ID、
     * Agent配置版本快照、系统提示词快照、模型配置快照、prompt哈希；初始状态为SUBMITTED已提交。
     * </p>
     *
     * @param a2aTaskId          A2A协议任务ID
     * @param a2aContextId       A2A协议上下文ID
     * @param input              Agent任务入参DTO，携带workbench侧taskId
     * @param agent              Agent配置实体
     * @param model              大模型配置实体
     * @param systemPromptSnapshot 本次执行使用的systemPrompt快照文本
     * @param promptHash         提示词哈希值，用于追踪prompt版本
     * @return 待入库的AgentExecutionEntity，状态为SUBMITTED
     */
    public static AgentExecutionEntity toEntity(String a2aTaskId, String a2aContextId, AgentTaskInputDTO input,
                                                 AgentEntity agent, ModelEntity model,
                                                 String systemPromptSnapshot, String promptHash) {
        AgentExecutionEntity entity = new AgentExecutionEntity();
        entity.setA2aTaskId(a2aTaskId);
        entity.setA2aContextId(a2aContextId);
        entity.setWorkbenchTaskId(input.workbenchTaskId());
        entity.setTraceId(TraceContext.getTelemetryTraceId());
        entity.setSpanId(TraceContext.getTelemetrySpanId());
        entity.setSpaceId(input.spaceId());
        entity.setAgentId(agent.getId());
        entity.setAgentNameSnapshot(agent.getName());
        // 记录Agent配置版本，后续配置修改不影响历史执行记录
        entity.setAgentConfigVersion(agent.getConfigVersion());
        entity.setMaxIterations(agent.getMaxIterations());
        entity.setExecutionTimeoutSeconds(agent.getExecutionTimeoutSeconds());
        entity.setSystemPromptSnapshot(systemPromptSnapshot);
        // 将模型关键配置序列化为JSON快照保存
        entity.setModelSnapshot(toModelSnapshot(model));
        entity.setModelConfigVersion(model.getConfigVersion());
        entity.setModelDisplayNameSnapshot(model.getDisplayName());
        entity.setPromptHash(promptHash);
        // 初始状态：已提交，还未开始执行
        entity.setStatus(AgentExecutionStatus.SUBMITTED.name());
        entity.setCancelRequested(Boolean.FALSE);
        // Token 消耗在模型返回后回填，未获取前保持 null
        entity.setInputTokens(null);
        entity.setInputTokensEstimated(Boolean.FALSE);
        entity.setCachedInputTokens(null);
        entity.setCachedInputTokensEstimated(Boolean.FALSE);
        entity.setOutputTokens(null);
        entity.setOutputTokensEstimated(Boolean.FALSE);
        return entity;
    }

    /**
     * 计算执行准备阶段已冻结配置的稳定哈希。哈希输入包含原始提示词，但查询接口只返回摘要。
     *
     * @param execution 已完成准备阶段字段填充的执行记录
     * @return 小写 SHA-256
     */
    public static String snapshotHash(AgentExecutionEntity execution) {
        String canonicalJson = execution.getExecutionSnapshotJson();
        if (canonicalJson == null || canonicalJson.isBlank()) {
            canonicalJson = snapshotJson(execution);
        }
        return SnapshotCanonicalV3Utils.hashEnvelope(canonicalJson);
    }

    /**
     * 生成可恢复、非机密的执行快照 v3 标准报文。
     * <p>
     * 将 Agent 执行实体的关键上下文快照字段组装为 {@link ExecutionSnapshot}，
     * 再通过 V3 规范化工具输出稳定序的规范 JSON 信封，用于后续哈希校验、JWT claim 绑定与执行恢复。
     * 注：快照仅保存配置快照/哈希，不包含会话密钥、用户凭证等敏感机密数据。
     *
     * @param execution Agent 执行记录实体
     * @return v3 规范化后的执行快照标准字符串（canonical envelope）
     */
    public static String snapshotJson(AgentExecutionEntity execution) {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
                execution.getAgentId(), execution.getAgentNameSnapshot(), execution.getAgentConfigVersion(),
                execution.getMaxIterations(),
                execution.getExecutionTimeoutSeconds(), execution.getSystemPromptSnapshot(),
                jsonValue(execution.getModelSnapshot()), execution.getModelConfigVersion(),
                jsonValue(execution.getSkillSnapshotJson()), execution.getSkillInstructionHash(),
                execution.getSkillSelectionMode(), execution.getSkillSelectionEffectiveMode(),
                execution.getSkillRouterModelId(), jsonValue(execution.getSelectedSkillVersionIdsJson()),
                jsonValue(execution.getSkillRouterSnapshotJson()),
                jsonValue(execution.getToolWhitelistSnapshot()),
                jsonValue(execution.getToolDefinitionSnapshotJson()),
                jsonValue(execution.getExternalMcpSnapshotJson()));
        return SnapshotCanonicalV3Utils.canonicalEnvelope(EXECUTION_SNAPSHOT_SCHEMA_VERSION, snapshot);
    }

    private static JsonNode jsonValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return JsonUtils.parse(value, JsonNode.class);
    }

    /**
     * 标记执行记录为工作中：Agent开始LLM推理与工具循环
     * <p>更新状态为WORKING，记录任务开始时间。</p>
     *
     * @param entity Agent执行记录实体
     */
    public static void markWorking(AgentExecutionEntity entity) {
        entity.setStatus(AgentExecutionStatus.WORKING.name());
        entity.setStartedAt(LocalDateTime.now());
    }

    /**
     * 标记任务正常完成，回填运行时结果、token用量、摘要、结束时间
     *
     * @param entity Agent执行记录实体
     * @param result Agent运行时返回结果对象，包含输入输出token、摘要
     */
    public static void complete(AgentExecutionEntity entity, AgentRuntimeResult result) {
        entity.setStatus(AgentExecutionStatus.COMPLETED.name());
        applyTokenUsage(entity, result.tokenUsage());
        entity.setResultSummary(result.summary());
        entity.setFinishedAt(LocalDateTime.now());
    }

    /**
     * 回填Token用量信息
     * @param entity Agent执行记录实体
     * @param usage Token用量对象
     */
    public static void applyTokenUsage(AgentExecutionEntity entity,
                                       TokenUsage usage) {
        if (usage == null) {
            return;
        }
        entity.setInputTokens(usage.input().value());
        entity.setInputTokensEstimated(isEstimated(usage.input()));
        entity.setCachedInputTokens(usage.cachedInput().value());
        entity.setCachedInputTokensEstimated(isEstimated(usage.cachedInput()));
        entity.setOutputTokens(usage.output().value());
        entity.setOutputTokensEstimated(isEstimated(usage.output()));
    }

    /**
     * 判断Token值是否为估算值
     * @param value Token值对象
     * @return true：估算值，false：实际值
     */
    private static boolean isEstimated(TokenValue value) {
        return value.source() == TokenValueSource.ESTIMATED;
    }

    /**
     * 标记任务执行失败，写入错误信息与结束时间
     *
     * @param entity       Agent执行记录实体
     * @param errorMessage 异常/失败描述信息
     */
    public static void fail(AgentExecutionEntity entity, String errorMessage) {
        entity.setStatus(AgentExecutionStatus.FAILED.name());
        entity.setErrorMessage(errorMessage);
        entity.setFinishedAt(LocalDateTime.now());
    }

    /**
     * 标记任务取消，设置取消请求标记、取消状态与结束时间
     *
     * @param entity Agent执行记录实体
     */
    public static void cancel(AgentExecutionEntity entity) {
        entity.setCancelRequested(Boolean.TRUE);
        entity.setStatus(AgentExecutionStatus.CANCELED.name());
        entity.setFinishedAt(LocalDateTime.now());
    }

    /**
     * 将模型关键配置序列化为JSON字符串快照
     * <p>保存本次执行使用的模型信息，后续模型配置变更不影响历史执行记录。</p>
     *
     * @param model        模型配置实体
     * @return JSON格式模型快照字符串
     * @throws IllegalStateException 序列化异常时抛出
     */
    private static String toModelSnapshot(ModelEntity model) {
        return JsonUtils.toJson(new ModelSnapshot(model.getId(), model.getProvider(),
                model.getAdapterType(), model.getModelKey(), model.getDisplayName(), model.getBaseUrl(),
                model.getOptionsJson(), model.getContextWindow(), model.getMaxOutputTokens(),
                model.getInputPricePerMillion(), model.getOutputPricePerMillion()));
    }

    /**
     * 模型快照内部记录类，仅提取执行时需要留存的模型字段
     *
     * @param id             模型ID
     * @param provider       模型服务商
     * @param adapterType    模型适配器类型
     * @param modelKey       模型调用标识key
     * @param displayName    展示名称
     * @param baseUrl        模型接口地址
     * @param maxOutputTokens 最大输出token
     */
    private record ModelSnapshot(Long id, String provider, String adapterType, String modelKey, String displayName,
                                 String baseUrl, String optionsJson, Long contextWindow,
                                 Long maxOutputTokens, BigDecimal inputPricePerMillion,
                                 java.math.BigDecimal outputPricePerMillion) {
    }

    /** execution snapshot v2 固定字段集合，不包含任务输入、运行标识、状态、时间和 Token 结果。 */
    private record ExecutionSnapshot(Long sourceAgentId, String agentNameSnapshot, Long agentConfigVersion,
                                     Integer maxIterations,
                                     Integer executionTimeoutSeconds, String systemPrompt,
                                     JsonNode model, Long modelConfigVersion, JsonNode skillSnapshot,
                                     String skillInstructionHash, String skillSelectionMode,
                                     String skillSelectionEffectiveMode, Long skillRouterModelId,
                                     JsonNode selectedSkillVersionIds, JsonNode skillRouterSnapshot,
                                     JsonNode toolWhitelist, JsonNode toolDefinitions,
                                     JsonNode externalMcpSnapshot) {
    }
}
