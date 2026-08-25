package com.agentdoc.agent.convertor;

import com.agentdoc.agent.enums.AgentExecutionStatus;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.enums.TokenValueSource;
import com.agentdoc.common.pojo.TokenValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

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
     * @param objectMapper       JSON序列化器，用于生成模型配置快照
     * @return 待入库的AgentExecutionEntity，状态为SUBMITTED
     */
    public static AgentExecutionEntity toEntity(String a2aTaskId, String a2aContextId, AgentTaskInputDTO input,
                                                AgentEntity agent, ModelEntity model,
                                                String systemPromptSnapshot, String promptHash,
                                                ObjectMapper objectMapper) {
        AgentExecutionEntity entity = new AgentExecutionEntity();
        entity.setA2aTaskId(a2aTaskId);
        entity.setA2aContextId(a2aContextId);
        entity.setWorkbenchTaskId(input.workbenchTaskId());
        entity.setAgentId(agent.getId());
        // 记录Agent配置版本，后续配置修改不影响历史执行记录
        entity.setAgentConfigVersion(agent.getConfigVersion());
        entity.setSystemPromptSnapshot(systemPromptSnapshot);
        // 将模型关键配置序列化为JSON快照保存
        entity.setModelSnapshot(toModelSnapshot(model, objectMapper));
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
        entity.setInputTokens(result.tokenUsage().input().value());
        entity.setInputTokensEstimated(isEstimated(result.tokenUsage().input()));
        entity.setCachedInputTokens(result.tokenUsage().cachedInput().value());
        entity.setCachedInputTokensEstimated(isEstimated(result.tokenUsage().cachedInput()));
        entity.setOutputTokens(result.tokenUsage().output().value());
        entity.setOutputTokensEstimated(isEstimated(result.tokenUsage().output()));
        entity.setResultSummary(result.summary());
        entity.setFinishedAt(LocalDateTime.now());
    }

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
     * @param objectMapper JSON序列化工具
     * @return JSON格式模型快照字符串
     * @throws IllegalStateException 序列化异常时抛出
     */
    private static String toModelSnapshot(ModelEntity model, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(new ModelSnapshot(model.getId(), model.getProvider(),
                    model.getAdapterType(), model.getModelKey(), model.getDisplayName(), model.getBaseUrl(),
                    model.getMaxOutputTokens()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型快照序列化失败", exception);
        }
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
                                 String baseUrl, Long maxOutputTokens) {
    }
}
