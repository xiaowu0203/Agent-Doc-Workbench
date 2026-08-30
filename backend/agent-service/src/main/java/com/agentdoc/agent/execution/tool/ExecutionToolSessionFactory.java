package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.ToolSource;
import com.agentdoc.agent.execution.tool.CancellationAwareToolCallback;
import com.agentdoc.agent.execution.tool.AuditingToolCallback;
import com.agentdoc.agent.execution.skill.SkillCandidate;
import com.agentdoc.agent.execution.application.AgentExecutionPersistenceService;
import com.agentdoc.agent.execution.audit.AgentExecutionToolAuditService;
import com.agentdoc.agent.execution.context.AgentRuntimeContext;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.agent.skill.storage.SkillResourceLoader;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ExecutionToolSession 工厂
 * <p>
 * 供自研Runtime与SpringAiAlibaba双Runtime共用；
 * 负责加载Skill资源、创建任务隔离的MCP工具会话，注入Skill内置本地资源工具（list/read资源），
 * 组装并返回{@link ExecutionToolSession}工具会话对象。
 * </p>
 * <p>
 * 业务逻辑：
 * <ol>
 * <li>加载本次Agent上下文对应的Skill快照资源；</li>
 * <li>打开任务级MCP工具会话，传入允许MCP白名单、取消信号、超时配置；</li>
 * <li>注册本地内置工具：resource_list（列出Skill可读资源）、resource_read（读取Skill文本资源）；</li>
 * <li>检测MCP远端工具与本地工具重名冲突，冲突抛出异常；</li>
 * <li>异常场景自动关闭已打开MCP会话，避免资源泄漏。</li>
 * </ol>
 * </p>
 */
@Component
@Slf4j
public class ExecutionToolSessionFactory {
    /** resource_list 工具入参JSON Schema：接收skillVersionId */
    private static final String LIST_SCHEMA = "{\"type\":\"object\",\"properties\":"
            + "{\"skillVersionId\":{\"type\":\"integer\"}}}";
    /** resource_read 工具入参JSON Schema：接收skillVersionId与资源path，两个字段必填 */
    private static final String READ_SCHEMA = "{\"type\":\"object\",\"properties\":"
            + "{\"skillVersionId\":{\"type\":\"integer\"},"
            + "\"path\":{\"type\":\"string\"}},"
            + "\"required\":[\"skillVersionId\",\"path\"]}";
    private static final String INSTRUCTION_SCHEMA = "{\"type\":\"object\",\"properties\":"
            + "{\"skillVersionId\":{\"type\":\"integer\"}},"
            + "\"required\":[\"skillVersionId\"]}";

    /** Skill资源加载器，负责读取Skill版本内的可读文本资源 */
    private final SkillResourceLoader resourceLoader;

    private final AgentExecutionPersistenceService executionPersistenceService;
    private final AgentExecutionToolAuditService toolAuditService;
    private final AgentConfigCryptoService cryptoService;
    private final McpEndpointSecurityValidator endpointValidator;

    public ExecutionToolSessionFactory(SkillResourceLoader resourceLoader,
                                       AgentExecutionPersistenceService executionPersistenceService,
                                       AgentExecutionToolAuditService toolAuditService,
                                       AgentConfigCryptoService cryptoService,
                                       McpEndpointSecurityValidator endpointValidator) {
        this.resourceLoader = resourceLoader;
        this.executionPersistenceService = executionPersistenceService;
        this.toolAuditService = toolAuditService;
        this.cryptoService = cryptoService;
        this.endpointValidator = endpointValidator;
    }

    /**
     * 开启一次Agent任务工具执行会话
     *
     * @param context          Agent运行时上下文（已固化配置、skill快照、MCP白名单）
     * @param cancelRequested  任务取消信号断言，用于工具执行阶段响应中断
     * @return 已组装完成的{@link ExecutionToolSession}，使用完毕必须close释放资源
     * @throws IllegalStateException 当远端MCP工具与Skill本地内置工具重名冲突时抛出
     * @throws RuntimeException      MCP会话打开、资源加载发生异常；内部会自动关闭已创建mcp会话防止泄露
     */
    public ExecutionToolSession open(AgentRuntimeContext context, BooleanSupplier cancelRequested) {
        // 根据技能执行快照，批量加载快照中所有绑定技能的可读资源
        SkillResourceLoader.LoadedSkillResources resources = resourceLoader.load(context.skillSnapshot());
        List<TaskScopedMcpTools> sessions = new ArrayList<>();
        try {
            // 打开任务隔离MCP会话，传入MCP服务地址、任务能力、超时、取消信号、允许MCP工具白名单
            TaskScopedMcpTools workbench = TaskScopedMcpTools.open(
                    context.taskInput().mcpServerUrl(),
                    context.taskInput().taskCapability(),
                    timeoutSeconds(context),
                    cancelRequested,
                    context.allowedMcpTools()
            );
            sessions.add(workbench);
            List<SourcedTool> tools = new ArrayList<>();
            workbench.callbacks().forEach(callback ->
                    tools.add(new SourcedTool(callback, ToolSource.MCP_REMOTE.name(),
                            McpConstant.WORKBENCH_SOURCE_KEY, null)));

            List<CompletableFuture<OpenedExternal>> futures = new ArrayList<>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (ExternalMcpConnection connection : context.externalMcpConnections()) {
                    List<String> allowed = allowedExternalTools(connection, context.allowedMcpTools());
                    if (allowed.isEmpty()) continue;
                    endpointValidator.validateExternal(connection.endpointUrl());
                    futures.add(CompletableFuture.supplyAsync(() -> openExternal(
                            connection, allowed, timeoutSeconds(context), cancelRequested), executor));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } catch (CompletionException exception) {
                futures.stream().filter(CompletableFuture::isDone)
                        .filter(value -> !value.isCompletedExceptionally())
                        .map(value -> value.getNow(null)).filter(Objects::nonNull)
                        .forEach(value -> closeQuietly(value.session()));
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) throw runtimeException;
                throw new IllegalStateException("外部 MCP 初始化失败", cause);
            }
            for (CompletableFuture<OpenedExternal> future : futures) {
                OpenedExternal opened = future.join();
                sessions.add(opened.session());
                opened.session().callbacks().forEach(callback -> tools.add(new SourcedTool(
                        callback, ToolSource.MCP_REMOTE.name(), opened.connection().serverKey(),
                        opened.connection().serverId())));
            }

            Map<Long, SkillCandidate> instructions = context.skillSnapshot().boundSkills().stream()
                    .filter(skill -> context.skillSnapshot().selectedSkillVersionIds()
                            .contains(skill.skillVersionId()))
                    .collect(Collectors.toMap(SkillCandidate::skillVersionId, value -> value));
            if (!instructions.isEmpty()) {
                requireNoConflict(tools, SkillConstant.INSTRUCTION_READ_TOOL);
                tools.add(new SourcedTool(new CancellationAwareToolCallback(
                        instructionCallback(instructions), cancelRequested),
                        ToolSource.SKILL_LOCAL.name(), McpConstant.SKILL_LOCAL_SOURCE_KEY, null));
            }

            // key: skillVersionId  value: path -> LoadedResource完整资源对象
            Map<Long, Map<String, SkillResourceLoader.LoadedResource>> resourceMap = resources.resourcesByVersionId();
            // key: skillVersionId  value: path -> 文本内容；只提取文本内容供read工具直接读取
            Map<Long, Map<String, String>> textResourceMap = new HashMap<>();
            resourceMap.forEach((versionId, values) ->
                    textResourceMap.put(versionId,
                            values.values().stream()
                                    .collect(Collectors.toMap(SkillResourceLoader.LoadedResource::path,
                                            SkillResourceLoader.LoadedResource::content))));

            // Skill存在可读资源，注册本地内置工具
            if (!resourceMap.isEmpty()) {
                // 防御校验：不允许远端MCP工具与Skill本地资源工具重名，避免冲突覆盖
                requireNoConflict(tools, SkillConstant.RESOURCE_LIST_TOOL);
                requireNoConflict(tools, SkillConstant.RESOURCE_READ_TOOL);
                // 注册列出Skill资源本地工具，包装取消信号感知
                tools.add(new SourcedTool(new CancellationAwareToolCallback(
                        listCallback(resourceMap), cancelRequested), ToolSource.SKILL_LOCAL.name(),
                        McpConstant.SKILL_LOCAL_SOURCE_KEY, null));
                // 注册读取Skill文本资源本地工具，包装取消信号感知
                tools.add(new SourcedTool(new CancellationAwareToolCallback(
                        readCallback(textResourceMap), cancelRequested), ToolSource.SKILL_LOCAL.name(),
                        McpConstant.SKILL_LOCAL_SOURCE_KEY, null));
            }
            requireUniqueToolNames(tools);
            List<ToolDefinitionSnapshot> definitions = tools.stream().map(tool -> {
                ToolDefinition definition = tool.callback().getToolDefinition();
                return new ToolDefinitionSnapshot(definition.name(), definition.description(),
                        definition.inputSchema(), tool.source(), tool.sourceKey(), tool.mcpServerId());
            }).toList();
            executionPersistenceService.updateToolDefinitionSnapshot(context.executionId(),
                    JsonUtils.toJson(definitions));

            AtomicInteger sequence = new AtomicInteger();
            List<ToolCallback> auditedCallbacks = tools.stream()
                    .map(tool -> new AuditingToolCallback(tool.callback(), context.executionId(),
                            tool.source(), tool.sourceKey(), tool.mcpServerId(),
                            sequence, toolAuditService))
                    .map(ToolCallback.class::cast)
                    .toList();
            return new ExecutionToolSession(sessions, auditedCallbacks);
        } catch (RuntimeException exception) {
            // 异常场景安全释放MCP会话，防止连接泄漏
            sessions.reversed().forEach(this::closeQuietly);
            throw exception;
        }
    }

    private OpenedExternal openExternal(ExternalMcpConnection connection, List<String> allowedTools,
                                        int timeoutSeconds, BooleanSupplier cancelRequested) {
        String token = McpAuthType.BEARER.name().equals(connection.authType())
                ? cryptoService.decrypt(connection.encryptedAuthToken()) : null;
        return new OpenedExternal(connection, TaskScopedMcpTools.openExternal(connection.endpointUrl(), token,
                connection.serverKey(), timeoutSeconds, cancelRequested, allowedTools,
                endpointValidator::validateResolved));
    }

    private List<String> allowedExternalTools(ExternalMcpConnection connection, List<String> allowedTools) {
        String prefix = connection.serverKey() + "__";
        List<String> remoteNames = allowedTools.stream().filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length())).distinct().sorted().toList();
        if (connection.bindingToolWhitelist() == null) return remoteNames;
        return remoteNames.stream().filter(connection.bindingToolWhitelist()::contains).toList();
    }

    private void requireUniqueToolNames(List<SourcedTool> tools) {
        Set<String> names = new HashSet<>();
        for (SourcedTool tool : tools) {
            if (!names.add(tool.callback().getToolDefinition().name())) {
                throw new IllegalStateException("模型工具名称冲突: " + tool.callback().getToolDefinition().name());
            }
        }
    }

    private void closeQuietly(TaskScopedMcpTools session) {
        try {
            session.close();
        } catch (RuntimeException exception) {
            log.warn("关闭任务级 MCP 会话失败", exception);
        }
    }

    private ToolCallback instructionCallback(Map<Long, SkillCandidate> instructions) {
        return callback(SkillConstant.INSTRUCTION_READ_TOOL, "读取当前执行中已选 Skill 的指令正文",
                INSTRUCTION_SCHEMA, input -> {
                    Long versionId = longValue(parse(input).get("skillVersionId"));
                    SkillCandidate skill = instructions.get(versionId);
                    if (skill == null) {
                        return "Skill 版本不存在或未被选择";
                    }
                    return "Skill: " + skill.name() + "@" + skill.versionNo()
                            + "\n--- BEGIN SKILL INSTRUCTIONS ---\n" + skill.instructionText()
                            + "\n--- END SKILL INSTRUCTIONS ---";
                });
    }

    private void requireNoConflict(List<SourcedTool> callbacks, String localToolName) {
        if (callbacks.stream().anyMatch(value -> value.callback().getToolDefinition().name().equals(localToolName))) {
            throw new IllegalStateException("远端 MCP 工具名称与 Skill 本地工具冲突: " + localToolName);
        }
    }

    private record SourcedTool(ToolCallback callback, String source, String sourceKey, Long mcpServerId) {
    }

    private record OpenedExternal(ExternalMcpConnection connection, TaskScopedMcpTools session) {
    }

    private record ToolDefinitionSnapshot(String name, String description, String inputSchema,
                                          String source, String sourceKey, Long mcpServerId) {
    }

    /**
     * 构建 resource_list 本地工具回调：列出指定SkillVersion下全部可读资源元信息
     *
     * @param resources 按skillVersionId分组的原始资源映射
     * @return ToolCallback 工具回调实例
     */
    private ToolCallback listCallback(Map<Long, Map<String, SkillResourceLoader.LoadedResource>> resources) {
        return callback(SkillConstant.RESOURCE_LIST_TOOL, "列出当前执行 Skill 的可读资源",
                LIST_SCHEMA, input -> {
                    Map<String, Object> args = parse(input);
                    Long versionId = longValue(args.get("skillVersionId"));
                    Map<String, SkillResourceLoader.LoadedResource> selected = resources.get(versionId);
                    if (selected == null) {
                        return "Skill 版本不存在或未绑定";
                    }
                    // 返回path、type、size元信息，按path字典序排序
                    return JsonUtils.toJson(selected.values().stream()
                            .sorted(Comparator.comparing(SkillResourceLoader.LoadedResource::path))
                            .map(value -> Map.of("path", value.path(), "type", value.type(), "size", value.size()))
                            .toList());
                });
    }

    /**
     * 构建 resource_read 本地工具回调：读取Skill内reference/example文本资源内容
     *
     * @param resources 按skillVersionId分组，path映射文本内容
     * @return ToolCallback 工具回调实例
     */
    private ToolCallback readCallback(Map<Long, Map<String, String>> resources) {
        return callback(SkillConstant.RESOURCE_READ_TOOL, "读取当前执行 Skill 的 reference/example 文本资源",
                READ_SCHEMA, input -> {
                    Map<String, Object> args = parse(input);
                    Long versionId = longValue(args.get("skillVersionId"));
                    String path = String.valueOf(args.getOrDefault("path", ""));
                    Map<String, String> selected = resources.get(versionId);
                    return selected == null ? "Skill 版本不存在或未绑定"
                            : selected.getOrDefault(path, "资源不存在");
                });
    }

    /**
     * 快速构造ToolCallback匿名实现
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param schema      入参JSON Schema字符串
     * @param function    工具执行逻辑，输入原始JSON字符串，返回工具输出文本
     * @return ToolCallback
     */
    private ToolCallback callback(String name, String description, String schema,
                                  Function<String, String> function) {
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(description).inputSchema(schema).build();
            }
            @Override public String call(String input) { return function.apply(input); }
        };
    }

    /**
     * 解析工具调用输入JSON为Map。
     *
     * @param input 工具原始入参JSON字符串，允许null
     * @return 参数Map
     * @throws IllegalArgumentException 输入不是合法 JSON 对象
     */
    private Map<String, Object> parse(String input) {
        Map<String, Object> parsed = JsonUtils.parse(input == null ? "{}" : input,
                new TypeReference<Map<String, Object>>() { });
        if (parsed == null) {
            throw new IllegalArgumentException("工具参数不是合法 JSON 对象");
        }
        return parsed;
    }

    /**
     * 安全转换对象为Long；格式错误、null返回null
     *
     * @param value 待转换对象
     * @return Long数值，转换失败返回null
     */
    private Long longValue(Object value) {
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 获取Agent执行超时秒数；优先取Agent配置，没有则使用全局默认值
     *
     * @param context Agent运行时上下文
     * @return 超时秒数
     */
    private int timeoutSeconds(AgentRuntimeContext context) {
        return context.agent().getExecutionTimeoutSeconds() == null
                ? AgentConstant.DEFAULT_EXECUTION_TIMEOUT_SECONDS
                : context.agent().getExecutionTimeoutSeconds();
    }
}
