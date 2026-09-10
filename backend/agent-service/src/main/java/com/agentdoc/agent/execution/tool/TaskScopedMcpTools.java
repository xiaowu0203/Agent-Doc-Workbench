package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.common.constant.JwtConstant;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Task维度MCP工具会话包装，实现AutoCloseable，支持资源自动释放
 * <p>
 * 为单次Agent任务建立独立MCP Sync客户端连接；每个Agent任务创建独立实例，任务生命周期内复用该会话。
 * 核心执行流程：
 * <ol>
 * <li>解析MCP服务URL，构建WebClient与HTTP‑Stream传输层，注入鉴权请求头；支持自定义DNS解析校验器；</li>
 * <li>将MCP‑initialize握手操作提交独立boundedElastic线程执行，避免阻塞调用方主线程；轮询检测超时、任务取消信号；</li>
 * <li>握手完成后从远端MCP服务发现全部工具；执行工具定义静态安全校验；按工具白名单过滤可使用工具；</li>
 * <li>工具回调多层装饰包装链：命名空间包装 → 任务取消拦截包装 → 返回结果大小限制包装；生成任务可用ToolCallback集合；</li>
 * <li>持有MCP客户端实例与包装完成的工具回调集合；close()关闭底层MCP客户端释放HTTP流连接资源。</li>
 * </ol>
 * </p>
 * <p>
 * 重要特性：
 * <ul>
 * <li>任务隔离：每个Agent任务对应独立MCP客户端实例，任务结束必须close，防止HTTP‑Stream连接泄漏；</li>
 * <li>可中断：初始化轮询阶段、进入open方法第一时间都会检测任务取消标记，收到取消信号抛出{@link AgentExecutionCanceledException}终止流程；</li>
 * <li>异常安全：初始化流程抛出运行时异常时，内部主动关闭McpSyncClient，避免残留连接；</li>
 * <li>工具管控：支持工具白名单过滤；工具定义做长度、大小校验，防御MCP服务返回超限数据；最大发现工具数量做上限控制；</li>
 * <li>工具装饰链：namespace命名空间隔离、{@link CancellationAwareToolCallback}任务取消拦截、{@link ToolResultSizeLimitCallback}返回结果字节截断保护。</li>
 * </ul>
 * </p>
 * <p>
 * 使用方式：配合 try‑with‑resources 使用，保证会话资源自动回收。
 * </p>
 */
@Slf4j
public final class TaskScopedMcpTools implements AutoCloseable {
    /**
     * initialize握手轮询间隔，单位毫秒；循环检测任务完成状态、取消标记、超时截止时间
     */
    private static final long INITIALIZE_POLL_INTERVAL_MILLIS = 25L;
    /**
     * MCP同步客户端实例，当前Agent任务专属会话；持有HTTP‑Stream传输连接；任务结束必须关闭
     */
    private final McpSyncClient client;
    /**
     * 经过多层装饰包装后的MCP工具回调列表；直接对外供给Agent执行链路使用；内部为不可变集合
     */
    private final List<ToolCallback> callbacks;

    private TaskScopedMcpTools(McpSyncClient client, List<ToolCallback> callbacks) {
        this.client = client;
        this.callbacks = List.copyOf(callbacks);
    }

    /**
     * 打开任务MCP会话（内部Task‑Capability鉴权模式）
     * <p>用于内部托管MCP服务；请求头携带 Authorization: Bearer {taskCapability}。</p>
     *
     * @param serverUrl MCP服务端完整绝对URL地址
     * @param taskCapability Task‑Capability鉴权令牌，放入Authorization Bearer头
     * @param timeoutSeconds MCP initialize握手超时时间(秒)
     * @param cancelRequested 任务取消状态源，返回true代表任务需要终止
     * @param allowedToolNames 工具白名单，仅列表内工具对当前任务可见；null 表示全部可见
     * @return TaskScopedMcpTools 实例，使用完毕务必调用close()，推荐try‑with‑resources
     * @throws AgentExecutionCanceledException 检测到任务已取消时抛出
     * @throws IllegalArgumentException URL格式非法抛出
     * @throws RuntimeException MCP连接、握手失败抛出，内部会自动关闭client释放资源
     */
    public static TaskScopedMcpTools open(String serverUrl, String taskCapability,
                                          int timeoutSeconds, BooleanSupplier cancelRequested,
                                          Collection<String> allowedToolNames) {
        return open(serverUrl, Map.of(HttpHeaders.AUTHORIZATION,
                        JwtConstant.TOKEN_TYPE_BEARER + " " + taskCapability), null,
                timeoutSeconds, cancelRequested, allowedToolNames);
    }

    /**
     * 打开外部MCP服务会话；自定义Bearer令牌、serverKey命名空间、地址解析校验器
     * <p>面向外部第三方MCP服务；支持传入resolvedAddressValidator，对解析出来的目标SocketAddress做校验拦截（防SSRF）。</p>
     *
     * @param serverUrl MCP服务完整绝对地址
     * @param bearerToken 外部服务Bearer鉴权token，可以为null/空白代表不携带鉴权头
     * @param queryParamName Query API Key 参数名；不使用时为空
     * @param queryParamValue Query API Key 明文；不使用时为空
     * @param serverKey 命名空间标识，不为null时工具会被{@link NamespacedToolCallback}包装；null则不做命名空间
     * @param timeoutSeconds initialize握手超时秒数
     * @param cancelRequested 任务取消状态源
     * @param allowedToolNames 工具白名单；null代表全部工具可见
     * @param resolvedAddressValidator DNS解析完成后地址校验回调，用于拦截内网地址防范SSRF；可为null
     * @return TaskScopedMcpTools实例
     */
    public static TaskScopedMcpTools openExternal(String serverUrl, String bearerToken,
                                                  String queryParamName, String queryParamValue, String serverKey,
                                                  int timeoutSeconds, BooleanSupplier cancelRequested,
                                                  Collection<String> allowedToolNames,
                                                  Consumer<SocketAddress> resolvedAddressValidator) {
        Map<String, String> headers = bearerToken == null || bearerToken.isBlank() ? Map.of()
                : Map.of(HttpHeaders.AUTHORIZATION, JwtConstant.TOKEN_TYPE_BEARER + " " + bearerToken);
        String transportUrl = appendQueryCredential(serverUrl, queryParamName, queryParamValue);
        try {
            return open(transportUrl, headers, serverKey, timeoutSeconds, cancelRequested, allowedToolNames,
                    resolvedAddressValidator);
        } catch (RuntimeException exception) {
            if (exception instanceof AgentExecutionCanceledException) {
                throw exception;
            }
            if (queryParamValue != null && !queryParamValue.isBlank()) {
                throw new IllegalStateException("MCP Query API Key 连接失败");
            }
            throw exception;
        }
    }

    private static String appendQueryCredential(String serverUrl, String name, String value) {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            return serverUrl;
        }
        return serverUrl + "?" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * open内部重载；无地址解析校验器版本
     * @param serverUrl MCP服务绝对URL
     * @param headers 自定义HTTP请求头集合
     * @param namespace 命名空间key；null不开启命名空间包装
     * @param timeoutSeconds initialize超时秒
     * @param cancelRequested 任务取消状态源
     * @param allowedToolNames 工具白名单
     * @return TaskScopedMcpTools
     */
    private static TaskScopedMcpTools open(String serverUrl, Map<String, String> headers, String namespace,
                                           int timeoutSeconds, BooleanSupplier cancelRequested,
                                           Collection<String> allowedToolNames) {
        return open(serverUrl, headers, namespace, timeoutSeconds, cancelRequested, allowedToolNames, null);
    }

    /**
     * MCP会话真正构建逻辑；所有公开open方法最终进入此方法
     * <p>执行步骤：
     * <ol>
     * <li>首先校验任务是否已经取消；</li>
     * <li>解析URL拆分为 baseUrl 与 path；</li>
     * <li>构建WebClient；若传入地址校验器，则自定义HttpClient，关闭代理、关闭重定向，DNS解析完成后执行地址校验；注入自定义请求头；</li>
     * <li>构建WebClientStreamableHttpTransport HTTP‑Stream传输层；</li>
     * <li>构建McpSyncClient同步客户端；</li>
     * <li>调用initialize()执行握手（任务提交boundedElastic线程轮询等待）；</li>
     * <li>再次校验任务取消；</li>
     * <li>发现远端MCP工具列表；校验工具总数上限；</li>
     * <li>工具流：静态定义校验 → 白名单过滤 → 命名空间包装 → 任务取消拦截包装 → 返回结果大小限制包装；</li>
     * <li>成功返回实例；任何RuntimeException发生时，如果client已经创建，则主动close客户端防止连接泄漏，再向上抛出异常。</li>
     * </ol>
     * </p>
     * @param serverUrl MCP服务完整绝对地址
     * @param headers HTTP自定义请求头
     * @param namespace 命名空间serverKey；null不包装命名空间
     * @param timeoutSeconds initialize握手超时秒数
     * @param cancelRequested 任务取消状态源
     * @param allowedToolNames 工具白名单集合
     * @param resolvedAddressValidator DNS解析后SocketAddress校验回调，可为null
     * @return TaskScopedMcpTools
     */
    private static TaskScopedMcpTools open(String serverUrl, Map<String, String> headers, String namespace,
                                           int timeoutSeconds, BooleanSupplier cancelRequested,
                                           Collection<String> allowedToolNames,
                                           Consumer<SocketAddress> resolvedAddressValidator) {
        // 校验是否任务取消
        requireNotCanceled(cancelRequested);

        // 解析MCP服务baseUrl与path路径段
        McpEndpoint endpoint = McpEndpoint.from(serverUrl);

        // 构建WebClient，注入鉴权Header
        WebClient.Builder webClient = WebClient.builder().baseUrl(endpoint.baseUrl());
        if (resolvedAddressValidator != null) {
            // 自定义HttpClient：关闭代理、关闭跟随重定向；DNS查询超时；解析出地址后执行外部校验回调（SSRF防护）
            HttpClient httpClient = HttpClient.create()
                    .noProxy()
                    .followRedirect(false)
                    .resolver(spec -> spec.queryTimeout(Duration.ofSeconds(
                            McpConstant.DNS_RESOLUTION_TIMEOUT_SECONDS)))
                    .doAfterResolve((connection, address) -> resolvedAddressValidator.accept(address));
            webClient.clientConnector(new ReactorClientHttpConnector(httpClient));
        }
        headers.forEach(webClient::defaultHeader);
        WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClient)
                .endpoint(endpoint.path()).build();

        McpSyncClient client = null;
        try {
            // 构建MCP同步客户端，设置客户端信息与请求超时
            client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation(AgentConstant.MCP_CLIENT_NAME,
                            AgentConstant.MCP_CLIENT_VERSION))
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds)).build();

            // 执行MCP initialize握手；握手运行在独立boundedElastic线程，轮询检测超时与任务取消
            initialize(client, timeoutSeconds, cancelRequested);
            // 握手完成后再次校验任务是否中途被取消
            requireNotCanceled(cancelRequested);

            // 获取远端MCP服务返回的原始工具回调数组
            ToolCallback[] discovered = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
            // 防御：MCP服务返回工具数量过大，直接拒绝
            if (discovered.length > McpConstant.MAX_DISCOVERED_TOOLS) {
                throw new IllegalStateException("MCP Server 暴露的工具数量超过限制");
            }
            List<ToolCallback> callbacks = Arrays.stream(discovered)
                    // 校验单个工具定义各项字段长度、大小安全阈值
                    .peek(TaskScopedMcpTools::validateDefinition)
                    // 根据白名单过滤工具；allowedToolNames为null代表全部放行
                    .filter(callback -> allowedToolNames == null
                            || allowedToolNames.contains(callback.getToolDefinition().name()))
                    // 命名空间包装：namespace不为null则添加serverKey__工具名前缀
                    .map(callback -> namespace == null ? callback
                            : new NamespacedToolCallback(callback, namespace))
                    // 包装任务取消感知：工具调用前检测任务取消标记
                    .map(callback -> new CancellationAwareToolCallback(callback, cancelRequested))
                    // 包装工具返回结果大小限制：防止工具返回超大报文压垮Agent上下文
                    .map(callback -> new ToolResultSizeLimitCallback(callback, McpConstant.MAX_TOOL_RESULT_BYTES))
                    .map(ToolCallback.class::cast).toList();
            return new TaskScopedMcpTools(client, callbacks);
        } catch (RuntimeException exception) {
            // 初始化流程出现任何运行时异常，主动关闭客户端，避免残留HTTP‑Stream连接泄漏
            if (client != null) {
                try {
                    client.close();
                } catch (RuntimeException closeException) {
                    log.warn("MCP 初始化失败后关闭客户端失败", closeException);
                }
            }
            throw exception;
        }
    }

    /**
     * 校验MCP工具定义字段安全阈值；工具名、描述长度、入参Schema字节大小超限抛出异常
     * <p>用于防御恶意MCP服务返回超大字段，造成内存、模型协议异常。</p>
     * @param callback MCP原始工具回调
     * @throws IllegalStateException 任意字段超过常量定义的安全上限抛出
     */
    private static void validateDefinition(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        if (definition.name() == null || definition.name().isBlank()
                || definition.name().length() > McpConstant.MAX_MODEL_TOOL_NAME_LENGTH
                || definition.description() != null
                && definition.description().length() > McpConstant.MAX_TOOL_DESCRIPTION_LENGTH
                || definition.inputSchema() != null
                && definition.inputSchema().getBytes(StandardCharsets.UTF_8).length
                > McpConstant.MAX_TOOL_SCHEMA_BYTES) {
            throw new IllegalStateException("MCP 工具定义超过安全限制");
        }
    }

    /**
     * 获取已经完成多层包装的MCP工具回调列表，直接供给Agent执行循环使用
     * @return 工具回调只读列表
     */
    public List<ToolCallback> callbacks() {
        return callbacks;
    }

    /**
     * AutoCloseable实现：关闭底层MCP客户端连接，释放HTTP‑Stream传输资源
     * <p>try‑with‑resources离开代码块自动调用；任务正常完成、异常终止均触发关闭。</p>
     */
    @Override public void close() {
        client.close();
    }

    /**
     * 校验任务取消标记，已请求取消直接抛出异常中断后续流程
     * @param cancelRequested 取消状态查询源
     * @throws AgentExecutionCanceledException 任务已取消抛出
     */
    private static void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) throw new AgentExecutionCanceledException();
    }

    /**
     * 执行MCP initialize握手；握手逻辑提交boundedElastic独立线程执行，不阻塞当前调用线程
     * <p>
     * 实现机制：
     * <ol>
     * <li>使用FutureTask包装client.initialize()，提交调度线程执行；</li>
     * <li>主线循环轮询任务状态，每{@link #INITIALIZE_POLL_INTERVAL_MILLIS}毫秒休眠；</li>
     * <li>循环内检测任务取消标记、检测超时截止时间；</li>
     * <li>任务完成调用task.get()获取结果，透传握手内部异常；</li>
     * <li>finally块：任务未完成则强制取消任务，释放调度资源。</li>
     * </ol>
     * </p>
     * @param client MCP同步客户端
     * @param timeoutSeconds initialize总超时秒数
     * @param cancelRequested 任务取消状态源
     * @throws IllegalStateException 超时、线程中断、握手异常包装为此异常
     * @throws AgentExecutionCanceledException 轮询期间检测任务取消抛出
     */
    private static void initialize(McpSyncClient client, int timeoutSeconds,
                                   BooleanSupplier cancelRequested) {
        // 将initialize操作提交到boundedElastic线程池执行
        FutureTask<Void> task = new FutureTask<>(() -> {
            client.initialize();
            return null;
        });
        // 调度任务
        Disposable scheduled = Schedulers.boundedElastic().schedule(task);
        // 计算超时截止时间点（纳秒）
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        try {
            while (!task.isDone()) {
                requireNotCanceled(cancelRequested);
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("MCP 初始化超时");
                }
                Thread.sleep(INITIALIZE_POLL_INTERVAL_MILLIS);
            }
            // 获取执行结果；内部异常会包装在ExecutionException中
            task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 初始化被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("MCP 初始化失败", cause);
        } finally {
            // 如果任务还未完成，强制取消线程任务，释放调度资源
            if (!task.isDone())
                task.cancel(true);
            scheduled.dispose();
        }
    }

    /**
     * MCP服务端地址解析记录record
     * <p>把传入完整serverUrl拆分为 baseUrl(scheme+authority) 和 path请求路径段；
     * path为空时使用系统默认MCP端点路径。</p>
     *
     * @param baseUrl 协议+域名部分，例：http://127.0.0.1:8000
     * @param path MCP HTTP‑transport请求path路径段
     */
    private record McpEndpoint(String baseUrl, String path) {
        /**
         * 从完整绝对URL解析出McpEndpoint
         * @param serverUrl 用户传入MCP服务完整地址
         * @return 拆分后的McpEndpoint
         * @throws IllegalArgumentException 传入非绝对URL抛出
         */
        private static McpEndpoint from(String serverUrl) {
            URI uri = URI.create(serverUrl);
            if (uri.getScheme() == null || uri.getAuthority() == null)
                throw new IllegalArgumentException("MCP Server URL 必须是绝对地址");
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            return new McpEndpoint(uri.getScheme() + "://" + uri.getAuthority(),
                    (path == null || path.isBlank() ? AgentConstant.DEFAULT_MCP_ENDPOINT : path)
                            + (query == null || query.isBlank() ? "" : "?" + query));
        }
    }
}
