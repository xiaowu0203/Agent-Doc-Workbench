package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面向工作台的 Agent 执行脱敏审计投影。
 *
 * @param id Agent 执行 ID
 * @param workbenchTaskId 工作台任务 ID
 * @param spaceId 空间 ID 快照
 * @param agentId Agent ID
 * @param agentName Agent 名称快照
 * @param agentConfigVersion Agent 配置版本快照
 * @param maxIterations 最大模型迭代次数快照
 * @param executionTimeoutSeconds 执行超时秒数快照
 * @param status Agent 执行状态
 * @param cancelRequested 是否已请求取消
 * @param promptHash Prompt 哈希
 * @param executionSnapshotHash 执行上下文快照哈希
 * @param model 模型快照
 * @param skill Skill 选择快照
 * @param toolDefinitions 最终暴露给模型的工具定义摘要
 * @param externalMcps 外部 MCP 脱敏快照
 * @param modelCalls 模型调用审计列表
 * @param toolCalls 工具调用审计列表
 * @param inputTokens 输入 Token
 * @param inputTokensEstimated 输入 Token 是否为估算值
 * @param cachedInputTokens 缓存输入 Token
 * @param cachedInputTokensEstimated 缓存输入 Token 是否为估算值
 * @param outputTokens 输出 Token
 * @param outputTokensEstimated 输出 Token 是否为估算值
 * @param startedAt Agent 执行开始时间
 * @param finishedAt Agent 执行结束时间
 * @param createdAt Agent 执行记录创建时间
 */
public record AgentExecutionAuditVO(
        Long id,
        Long workbenchTaskId,
        Long spaceId,
        Long agentId,
        String agentName,
        Long agentConfigVersion,
        Integer maxIterations,
        Integer executionTimeoutSeconds,
        String status,
        Boolean cancelRequested,
        String promptHash,
        String executionSnapshotHash,
        ModelSnapshot model,
        SkillSnapshot skill,
        List<ToolDefinitionSnapshot> toolDefinitions,
        List<ExternalMcpSnapshot> externalMcps,
        List<ModelCall> modelCalls,
        List<ToolCall> toolCalls,
        Long inputTokens,
        Boolean inputTokensEstimated,
        Long cachedInputTokens,
        Boolean cachedInputTokensEstimated,
        Long outputTokens,
        Boolean outputTokensEstimated,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt) {

    /**
     * @param id 模型 ID
     * @param modelKey 模型调用标识
     * @param displayName 模型展示名称快照
     * @param configVersion 模型配置版本快照
     */
    public record ModelSnapshot(Long id, String modelKey, String displayName, Long configVersion) { }

    /**
     * @param configuredMode Agent 配置的 Skill 选择模式
     * @param effectiveMode 本次实际 Skill 选择模式
     * @param instructionHash Skill 指令集合哈希
     * @param routerModelId Skill Router 模型 ID
     * @param routerDurationMs Skill Router 耗时毫秒
     * @param routerFallbackReason Skill Router 降级原因
     * @param routerInputHash Skill Router 输入哈希
     * @param routerResponseHash Skill Router 输出哈希
     * @param boundSkills 执行时绑定的 Skill 版本摘要
     * @param selectedSkillVersionIds 本次选中的 Skill 版本 ID
     */
    public record SkillSnapshot(
            String configuredMode,
            String effectiveMode,
            String instructionHash,
            Long routerModelId,
            Long routerDurationMs,
            String routerFallbackReason,
            String routerInputHash,
            String routerResponseHash,
            List<BoundSkill> boundSkills,
            List<Long> selectedSkillVersionIds) { }

    /**
     * @param skillId Skill ID
     * @param skillVersionId Skill 版本 ID
     * @param versionNo Skill 版本号
     * @param name Skill 技术名称
     * @param activationDescription Skill 激活描述
     * @param packageSha256 Skill 包哈希
     */
    public record BoundSkill(Long skillId, Long skillVersionId, Integer versionNo, String name,
                             String activationDescription, String packageSha256) { }

    /**
     * @param name 工具名称
     * @param source 工具来源类型
     * @param sourceKey 工具来源标识
     * @param mcpServerId 外部 MCP 服务 ID
     */
    public record ToolDefinitionSnapshot(String name, String source, String sourceKey, Long mcpServerId) { }

    /**
     * @param serverId 外部 MCP 服务 ID
     * @param serverKey 外部 MCP 稳定标识
     * @param configVersion MCP 配置版本
     * @param endpointSha256 MCP 端点哈希
     * @param authType 认证类型
     * @param toolWhitelist 绑定工具白名单
     */
    public record ExternalMcpSnapshot(Long serverId, String serverKey, Long configVersion,
                                      String endpointSha256, String authType, List<String> toolWhitelist) { }

    /**
     * @param sequenceNo 模型调用序号
     * @param modelId 模型 ID
     * @param modelConfigVersion 模型配置版本
     * @param modelKey 模型调用标识
     * @param maxOutputTokens 本轮最大输出 Token
     * @param temperature 本轮温度参数
     * @param streaming 是否流式调用
     * @param messagesSha256 消息序列哈希
     * @param messagesSize 消息序列字节数
     * @param responseSha256 响应哈希
     * @param responseSize 响应字节数
     * @param status 调用状态
     * @param errorType 错误类型
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    public record ModelCall(Integer sequenceNo, Long modelId, Long modelConfigVersion, String modelKey,
                            Integer maxOutputTokens, Double temperature, Boolean streaming,
                            String messagesSha256, Long messagesSize, String responseSha256,
                            Long responseSize, String status, String errorType,
                            LocalDateTime startedAt, LocalDateTime finishedAt) { }

    /**
     * @param sequenceNo 工具调用序号
     * @param toolName 工具名称
     * @param toolSource 工具来源类型
     * @param toolSourceKey 工具来源标识
     * @param mcpServerId 外部 MCP 服务 ID
     * @param skillVersionId Skill 本地工具指向的版本 ID
     * @param argumentsSha256 参数哈希
     * @param argumentsSize 参数字节数
     * @param resultSha256 结果哈希
     * @param resultSize 结果字节数
     * @param status 调用状态
     * @param errorType 错误类型
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     */
    public record ToolCall(Integer sequenceNo, String toolName, String toolSource, String toolSourceKey,
                           Long mcpServerId, Long skillVersionId, String argumentsSha256,
                           Long argumentsSize, String resultSha256, Long resultSize, String status,
                           String errorType, LocalDateTime startedAt, LocalDateTime finishedAt) { }
}
