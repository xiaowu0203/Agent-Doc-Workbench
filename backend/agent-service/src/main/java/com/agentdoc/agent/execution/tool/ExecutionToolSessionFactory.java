package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.ToolSource;
import com.agentdoc.agent.execution.skill.SkillCandidate;
import com.agentdoc.agent.execution.application.AgentExecutionPersistenceService;
import com.agentdoc.agent.execution.audit.AgentExecutionToolAuditService;
import com.agentdoc.agent.observability.AgentTelemetry;
import com.agentdoc.agent.execution.context.AgentRuntimeContext;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.agent.skill.storage.SkillResourceLoader;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.ExecutionArtifactAppendDTO;
import com.agentdoc.common.feign.vo.ExecutionArtifactAppendVO;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.logging.LogSanitizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
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
    private final AgentTelemetry telemetry;
    private final TaskFeign taskFeign;

    public ExecutionToolSessionFactory(SkillResourceLoader resourceLoader,
                                       AgentExecutionPersistenceService executionPersistenceService,
                                       AgentExecutionToolAuditService toolAuditService,
                                       AgentConfigCryptoService cryptoService,
                                       McpEndpointSecurityValidator endpointValidator,
                                       AgentTelemetry telemetry,
                                       TaskFeign taskFeign) {
        this.resourceLoader = resourceLoader;
        this.executionPersistenceService = executionPersistenceService;
        this.toolAuditService = toolAuditService;
        this.cryptoService = cryptoService;
        this.endpointValidator = endpointValidator;
        this.telemetry = telemetry;
        this.taskFeign = taskFeign;
    }

    /**
     * 开启一次Agent任务工具执行会话（收集Skill、MCP相关工具集）
     *
     * @param context          Agent运行时上下文（已固化配置、skill快照、MCP白名单）
     * @param cancelRequested  任务取消信号断言，用于工具执行阶段响应中断
     * @return 已组装完成的{@link ExecutionToolSession}，使用完毕必须close释放资源
     * @throws IllegalStateException 当远端MCP工具与Skill本地内置工具重名冲突时抛出
     * @throws RuntimeException      MCP会话打开、资源加载发生异常；内部会自动关闭已创建mcp会话防止泄露
     */
    public ExecutionToolSession open(AgentRuntimeContext context, BooleanSupplier cancelRequested) {
        // 判断当前任务执行模式是否为【ISOLATED隔离模式】，隔离模式用于预校验/快照哈希生成，禁止产生真实外部副作用
        boolean isolated = TaskExecutionMode.ISOLATED.name().equals(context.taskInput().executionMode());
        // 若为隔离执行模式且存在外部MCP连接配置，直接抛异常：隔离环境不允许初始化外部MCP，避免调用外部服务产生真实副作用
        if (isolated && !context.externalMcpConnections().isEmpty()) {
            throw new IllegalStateException("隔离执行禁止初始化外部 MCP");
        }
        // 根据技能执行快照，批量加载快照中所有绑定技能的可读资源
        SkillResourceLoader.LoadedSkillResources resources = resourceLoader.load(context.skillSnapshot());

        // 保存所有已建立的MCP会话，用于方法异常时统一回滚关闭，防止连接泄露
        List<TaskScopedMcpTools> sessions = new ArrayList<>();
        try {
            // 建立工作台内置MCP会话（文档操作等工作台原生工具）
            // 埋点链路追踪Span，托管MCP连接生命周期；传入超时、取消信号、允许工具白名单
            TaskScopedMcpTools workbench = telemetry.connectMcp(context.executionId(), null,
                    () -> TaskScopedMcpTools.open(
                    context.taskInput().mcpServerUrl(),
                    context.taskInput().taskCapability(),
                    timeoutSeconds(context),
                    cancelRequested,
                    context.allowedMcpTools()
            ));
            sessions.add(workbench);

            // 校验工作台回调非空：说明Agent未开通工作台文档工具权限，检查白名单与Skill配置
            if (workbench.callbacks().isEmpty()) {
                throw new IllegalStateException(
                        "当前 Agent 未获得 Workbench 文档工具权限，请检查 Agent 工具白名单和 Skill allowed-tools 配置");
            }

            // 收集【workbench】注册的全部回调工具，封装为带来源标记的 SourcedTool
            List<SourcedTool> tools = new ArrayList<>();
            // 生成产物自增序号，用于隔离模式下捕获输出 artifact 的顺序编号
            AtomicInteger artifactSequence = new AtomicInteger();

            // 遍历工作台所有工具回调
            workbench.callbacks().forEach(callback -> {
                // 隔离模式：使用捕获包装器拦截回调调用、记录产物；非隔离模式直接使用原始回调
                ToolCallback effective = isolated
                        ? captureOnlyCallback(callback, context, artifactSequence) : callback;
                // 封装工具实例，标记来源为远程MCP，来源标识为【workbench】
                tools.add(new SourcedTool(effective, ToolSource.MCP_REMOTE.name(),
                        McpConstant.WORKBENCH_SOURCE_KEY, null));
            });

            // 异步批量初始化外部MCP服务连接
            List<CompletableFuture<OpenedExternal>> futures = new ArrayList<>();
            // 捕获当前OTel上下文，虚拟线程中透传链路追踪
            Context parentContext = Context.current();

            // 使用虚拟线程池并发拉起外部MCP连接，缩短启动耗时
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (ExternalMcpConnection connection : context.externalMcpConnections()) {
                    // 根据全局白名单过滤该MCP服务允许调用的工具集合；允许列表为空则跳过该服务，不建立连接
                    List<String> allowed = allowedExternalTools(connection, context.allowedMcpTools());
                    if (allowed != null && allowed.isEmpty())
                        continue;

                    // 外部MCP地址安全校验，拦截非法/高危端点
                    endpointValidator.validateExternal(connection.endpointUrl());

                    // 异步打开外部MCP会话，透传trace上下文
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try (Scope ignored = parentContext.makeCurrent()) {
                            return telemetry.connectMcp(context.executionId(), connection.serverId(),
                                    () -> openExternal(connection, allowed,
                                            timeoutSeconds(context), cancelRequested));
                        }
                    }, executor));
                }

                // 阻塞等待所有外部MCP连接初始化完成
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } catch (CompletionException exception) {
                // 并发异常分支：把已经成功建立的MCP会话全部关闭，避免残留连接泄漏
                futures.stream().filter(CompletableFuture::isDone)
                        .filter(value -> !value.isCompletedExceptionally())
                        .map(value -> value.getNow(null)).filter(Objects::nonNull)
                        .forEach(value -> closeQuietly(value.session()));
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) throw runtimeException;
                throw new IllegalStateException("外部 MCP 初始化失败", cause);
            }

            // 遍历所有成功初始化的外部MCP，注册它们提供的工具，标记对应的服务来源信息
            for (CompletableFuture<OpenedExternal> future : futures) {
                OpenedExternal opened = future.join();
                sessions.add(opened.session());
                opened.session().callbacks().forEach(callback -> tools.add(new SourcedTool(
                        callback, ToolSource.MCP_REMOTE.name(), opened.connection().serverKey(),
                        opened.connection().serverId())));
            }

            // 从快照中筛选出当前选中版本的Skill指令集合，按skillVersionId构建Map
            Map<Long, SkillCandidate> instructions = context.skillSnapshot().boundSkills().stream()
                    .filter(skill -> context.skillSnapshot().selectedSkillVersionIds()
                            .contains(skill.skillVersionId()))
                    .collect(Collectors.toMap(SkillCandidate::skillVersionId, value -> value));

            // 如果存在Skill指令，注册【指令读取本地工具】；先做重名校验，防止和MCP工具冲突
            if (!instructions.isEmpty()) {
                requireNoConflict(tools, SkillConstant.INSTRUCTION_READ_TOOL);
                // 包装取消信号感知，任务取消时可中断工具调用
                tools.add(new SourcedTool(new CancellationAwareToolCallback(
                        instructionCallback(instructions), cancelRequested),
                        ToolSource.SKILL_LOCAL.name(), McpConstant.SKILL_LOCAL_SOURCE_KEY, null));
            }

            // key: skillVersionId, value: path -> 完整资源对象
            Map<Long, Map<String, SkillResourceLoader.LoadedResource>> resourceMap = resources.resourcesByVersionId();
            // key: skillVersionId  value: path -> 文本内容；提取文本资源，只保留路径和文本内容，供资源读取工具使用
            Map<Long, Map<String, String>> textResourceMap = new HashMap<>();
            resourceMap.forEach((versionId, values) ->
                    textResourceMap.put(versionId,
                            values.values().stream()
                                    .collect(Collectors.toMap(SkillResourceLoader.LoadedResource::path,
                                            SkillResourceLoader.LoadedResource::content))));

            // Skill存在可读资源，注册本地内置工具
            if (!resourceMap.isEmpty()) {
                // 冲突防御：远端MCP不能注册和本地资源工具同名的函数，防止覆盖
                requireNoConflict(tools, SkillConstant.RESOURCE_LIST_TOOL);
                requireNoConflict(tools, SkillConstant.RESOURCE_READ_TOOL);
                // 注册资源列表工具，支持取消中断
                tools.add(new SourcedTool(new CancellationAwareToolCallback(
                        listCallback(resourceMap), cancelRequested), ToolSource.SKILL_LOCAL.name(),
                        McpConstant.SKILL_LOCAL_SOURCE_KEY, null));
                // 注册资源读取工具，支持取消中断
                tools.add(new SourcedTool(new CancellationAwareToolCallback(
                        readCallback(textResourceMap), cancelRequested), ToolSource.SKILL_LOCAL.name(),
                        McpConstant.SKILL_LOCAL_SOURCE_KEY, null));
            }
            // 全局强校验：所有已注册工具名称必须唯一，不同来源之间也不能重名
            requireUniqueToolNames(tools);

            // 构建工具定义快照，按工具名、sourceKey稳定排序，保证canonical序列化后哈希稳定，用于JWT绑定校验
            List<ToolDefinitionSnapshot> definitions = tools.stream().map(tool -> {
                ToolDefinition definition = tool.callback().getToolDefinition();
                return new ToolDefinitionSnapshot(definition.name(), definition.description(),
                        definition.inputSchema(), tool.source(), tool.sourceKey(), tool.mcpServerId());
            }).sorted(Comparator.comparing(ToolDefinitionSnapshot::name)
                    .thenComparing(ToolDefinitionSnapshot::sourceKey,
                            Comparator.nullsFirst(String::compareTo)))
                    .toList();

            // 持久化本次执行的工具定义快照，用于审计、回放、快照校验
            executionPersistenceService.updateToolDefinitionSnapshot(context.executionId(),
                    JsonUtils.toJson(definitions));

            // 全局调用序列号，每一次工具调用自增，用于审计日志顺序
            AtomicInteger sequence = new AtomicInteger();

            // 给全部工具套上审计埋点包装器，记录调用入参、返回、耗时、异常、链路信息
            List<ToolCallback> auditedCallbacks = tools.stream()
                    .map(tool -> new AuditingToolCallback(tool.callback(), context.executionId(),
                            tool.source(), tool.sourceKey(), tool.mcpServerId(),
                            sequence, toolAuditService, telemetry))
                    .map(ToolCallback.class::cast)
                    .toList();

            // 组装会话对象返回，上层调用方必须调用close释放所有MCP连接
            return new ExecutionToolSession(sessions, auditedCallbacks);
        } catch (RuntimeException exception) {
            // 任何运行时异常：逆序关闭已经打开的MCP会话，保证资源释放，防止连接泄漏
            sessions.reversed().forEach(this::closeQuietly);
            throw exception;
        }
    }

    /**
     * 打开单个外部MCP服务连接，根据配置的鉴权类型解密并组装认证参数
     *
     * @param connection     外部MCP连接配置实体
     * @param allowedTools   当前允许调用的工具白名单（已剔除服务前缀）
     * @param timeoutSeconds MCP调用超时秒数
     * @param cancelRequested 任务取消信号断言
     * @return 封装了连接配置与已建立会话的 {@link OpenedExternal}
     */
    private OpenedExternal openExternal(ExternalMcpConnection connection, List<String> allowedTools,
                                        int timeoutSeconds, BooleanSupplier cancelRequested) {
        // 根据鉴权类型解密存储的加密凭证；无鉴权时凭证置null
        String credential = McpAuthType.NONE.name().equals(connection.authType())
                ? null : cryptoService.decrypt(connection.encryptedAuthToken());
        // Bearer模式：解密后的凭证作为Authorization Bearer令牌
        String bearerToken = McpAuthType.BEARER.name().equals(connection.authType()) ? credential : null;
        // QueryParam模式：解密后的凭证作为URL查询参数值
        String queryParamValue = McpAuthType.QUERY_PARAM.name().equals(connection.authType())
                ? credential : null;
        // 建立外部MCP会话，传入端点、鉴权信息、白名单、端点安全校验器
        return new OpenedExternal(connection, TaskScopedMcpTools.openExternal(connection.endpointUrl(), bearerToken,
                connection.authParamName(), queryParamValue, connection.serverKey(),
                timeoutSeconds, cancelRequested, allowedTools,
                endpointValidator::validateResolved));
    }

    /**
     * 过滤当前外部MCP服务可访问的工具集合，实现双层白名单校验
     * <p>
     * 全局白名单带服务前缀(serverKey__)，先剥离前缀得到远端工具名；
     * 再和该MCP自身绑定白名单做交集，取二者都放行的工具。
     *
     * @param connection   外部MCP连接配置
     * @param allowedTools 全局MCP工具白名单（带serverKey__前缀），null表示不启用全局白名单过滤
     * @return 当前服务最终允许调用的远端工具名列表（已去重、排序，无前缀）
     */
    private List<String> allowedExternalTools(ExternalMcpConnection connection, List<String> allowedTools) {
        // 全局白名单未配置：直接使用该MCP自身绑定的工具白名单
        if (allowedTools == null)
            return connection.bindingToolWhitelist();
        String prefix = connection.serverKey() + "__";
        // 过滤出属于当前服务前缀的条目，剥离前缀、去重并排序
        List<String> remoteNames = allowedTools.stream().filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length())).distinct().sorted().toList();
        // 当前MCP无独立绑定白名单：直接返回全局过滤后的结果
        if (connection.bindingToolWhitelist() == null)
            return remoteNames;
        // 取交集：同时满足全局白名单 + 该MCP自身绑定白名单
        return remoteNames.stream().filter(connection.bindingToolWhitelist()::contains).toList();
    }

    /**
     * 全局工具名校验：所有来源的工具名称不能重复
     * <p>
     * 不是静默去重；一旦发现同名工具，直接抛出异常终止会话创建，
     * 避免模型调用时因重名产生寻址歧义。
     *
     * @param tools 待校验的全量工具集合（MCP远端 + Skill本地内置）
     * @throws IllegalStateException 存在重名工具时抛出
     */
    private void requireUniqueToolNames(List<SourcedTool> tools) {
        Set<String> names = new HashSet<>();
        for (SourcedTool tool : tools) {
            // add返回false代表元素已存在，触发冲突异常
            if (!names.add(tool.callback().getToolDefinition().name())) {
                throw new IllegalStateException("模型工具名称冲突: " + tool.callback().getToolDefinition().name());
            }
        }
    }

    /**
     * 静默关闭MCP会话，吞掉关闭阶段异常并打印警告日志，
     * 用于异常回滚、资源清理场景，关闭失败不向上抛主流程异常。
     *
     * @param session 待关闭的任务级MCP会话
     */
    private void closeQuietly(TaskScopedMcpTools session) {
        try {
            session.close();
        } catch (RuntimeException exception) {
            // 日志脱敏，避免敏感信息泄露
            log.warn("关闭任务级 MCP 会话失败 type={} stack={}", exception.getClass().getName(),
                    LogSanitizer.sanitizeThrowable(exception));
        }
    }

    /**
     * 构造「读取已选中Skill指令文本」的本地内置工具回调
     * <p>
     * 运行时根据传入 skillVersionId 返回对应Skill的指令内容；
     * 属于Skill本地工具，不和远端MCP走网络调用。
     *
     * @param instructions 当前执行快照中被选中的Skill版本映射：skillVersionId -> SkillCandidate
     * @return 指令读取工具回调实例
     */
    private ToolCallback instructionCallback(Map<Long, SkillCandidate> instructions) {
        return callback(SkillConstant.INSTRUCTION_READ_TOOL, "读取当前执行中已选 Skill 的指令正文",
                INSTRUCTION_SCHEMA, input -> {
                    Long versionId = longValue(parse(input).get("skillVersionId"));
                    SkillCandidate skill = instructions.get(versionId);
                    if (skill == null) {
                        return "Skill 版本不存在或未被选择";
                    }
                    // 拼接名称、版本、指令正文，返回给模型
                    return "Skill: " + skill.name() + "@" + skill.versionNo()
                            + "\n--- BEGIN SKILL INSTRUCTIONS ---\n" + skill.instructionText()
                            + "\n--- END SKILL INSTRUCTIONS ---";
                });
    }

    /**
     * 隔离模式（ISOLATED）专用捕获包装回调，仅针对工作台写类工具生效。
     * <p>
     * 不执行真实文档写入，只把变更参数持久化为执行产物artifact，
     * 记录payload哈希与递增序号，用于生成稳定执行快照、A2A令牌校验；
     * 非写类工具直接透传原始回调。
     *
     * @param original         原始工作台工具回调
     * @param context          Agent运行时上下文
     * @param artifactSequence 全局自增产物序列号，保证捕获顺序稳定、快照可复现
     * @return 捕获模式包装后的回调；非目标工具返回原回调
     */
    ToolCallback captureOnlyCallback(ToolCallback original, AgentRuntimeContext context,
                                     AtomicInteger artifactSequence) {
        String toolName = original.getToolDefinition().name();
        // 根据工具名映射产物类型，仅工作台变更/草稿工具开启捕获逻辑
        String artifactType = switch (toolName) {
            case McpConstant.WORKBENCH_PROPOSE_CHANGES_TOOL -> "CHANGE_PROPOSAL";
            case McpConstant.WORKBENCH_APPLY_DRAFT_CHANGES_TOOL -> "DRAFT_CHANGES";
            default -> null;
        };
        // 不属于需要捕获的写工具，直接返回原始回调
        if (artifactType == null) {
            return original;
        }
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                // 工具元信息完全复用原始定义，保证快照hash一致
                return original.getToolDefinition();
            }

            @Override
            public String call(String input) {
                JsonNode payload = JsonUtils.parse(input, JsonNode.class);
                if (payload == null || !payload.isObject()) {
                    throw new IllegalArgumentException("Workbench 写工具参数不是合法 JSON 对象");
                }
                int schemaVersion = 1;
                String payloadJson = JsonUtils.toJson(payload);
                // 对入参做稳定序列化并计算快照哈希
                String payloadHash = StableSnapshotUtils.snapshotHash(schemaVersion, payload);
                // 产物序号自增，保证捕获顺序稳定
                int sequenceNo = artifactSequence.incrementAndGet();
                // 限制单次执行最大捕获产物数量，防DOS
                if (sequenceNo > McpConstant.MAX_CAPTURE_ARTIFACT_COUNT) {
                    throw new IllegalStateException("单次执行候选产物数量超过限制");
                }
                // 调用feign远程接口保存隔离模式下的候选产物，不改动真实文档
                var result = taskFeign.appendExecutionArtifact(context.taskInput().workbenchTaskId(),
                        JwtConstant.TOKEN_TYPE_BEARER + " " + context.taskInput().taskCapability(),
                        context.taskInput().taskCapability(),
                        new ExecutionArtifactAppendDTO(context.executionId(), context.taskInput().sourceTaskId(),
                                sequenceNo, null, artifactType, schemaVersion, payloadJson, payloadHash));
                // 保存失败直接抛异常，隔离快照必须完整才能用于后续哈希与令牌签发
                if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
                    throw new IllegalStateException("隔离执行候选产物保存失败");
                }
                ExecutionArtifactAppendVO artifact = result.data();
                // 返回标记：已捕获、未真实提交变更，携带产物ID与哈希
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("captured", true);
                response.put("artifactId", artifact.artifactId());
                response.put("artifactType", artifact.artifactType());
                response.put("payloadSha256", artifact.payloadSha256());
                response.put("message", "候选变更已捕获，未写入正式文档或草稿");
                return JsonUtils.toJson(response);
            }
        };
    }

    /**
     * 前置冲突检查：防止远端MCP工具和Skill本地内置工具重名
     * <p>
     * 本地工具（指令读取、资源列表、资源读取）是保留函数名，
     * 如果外部MCP注册同名工具，直接拒绝启动会话，避免覆盖。
     *
     * @param callbacks   已经收集到的远端MCP工具集合
     * @param localToolName Skill本地内置工具保留名称
     * @throws IllegalStateException 发现重名时抛出
     */
    private void requireNoConflict(List<SourcedTool> callbacks, String localToolName) {
        if (callbacks.stream().anyMatch(value -> value.callback().getToolDefinition().name().equals(localToolName))) {
            throw new IllegalStateException("远端 MCP 工具名称与 Skill 本地工具冲突: " + localToolName);
        }
    }

    /**
     * 带来源标记的工具记录，用于区分工具来自MCP远端还是Skill本地。
     *
     * @param callback    工具调用回调实现
     * @param source      来源枚举名称 {@link ToolSource}
     * @param sourceKey   来源标识：工作台/serverKey/SKILL_LOCAL
     * @param mcpServerId 外部MCP服务ID，本地工具为null
     */
    private record SourcedTool(ToolCallback callback, String source, String sourceKey, Long mcpServerId) { }

    /**
     * 已打开的外部MCP会话包装记录，绑定原始连接配置与会话实例。
     *
     * @param connection 外部MCP连接配置
     * @param session    已建立的任务级MCP会话
     */
    private record OpenedExternal(ExternalMcpConnection connection, TaskScopedMcpTools session) { }

    /**
     * 工具定义快照实体，用于序列化落库、生成稳定hash、A2A JWT绑定校验。
     * 字段顺序固定，序列化时按name+sourceKey排序，保证相同工具集合产出相同摘要。
     *
     * @param name         工具函数名
     * @param description  工具描述
     * @param inputSchema  JSON Schema字符串
     * @param source       来源类型
     * @param sourceKey    来源标识
     * @param mcpServerId  MCP服务ID
     */
    private record ToolDefinitionSnapshot(String name, String description, String inputSchema,
                                          String source, String sourceKey, Long mcpServerId) { }

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
