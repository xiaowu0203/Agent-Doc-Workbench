package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.execution.tool.CancellationAwareToolCallback;
import com.agentdoc.common.constant.JwtConstant;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

/**
 * Task维度MCP工具会话包装，实现AutoCloseable，支持资源自动释放
 * <p>
 * 为单次Agent任务建立独立MCP Sync客户端连接：
 * <ol>
 * <li>建立HTTP流Transport，携带task‑capability鉴权令牌访问MCP服务端</li>
 * <li>执行MCP initialize握手，带超时控制与任务取消检测</li>
 * <li>拉取MCP服务端全部工具，外层包装 {@link CancellationAwareToolCallback} 增加任务取消拦截</li>
 * <li>持有MCP客户端与包装后的ToolCallback列表；close()时关闭MCP连接释放资源</li>
 * </ol>
 * </p>
 * <p>
 * 重要特性：
 * <ul>
 * <li>每个任务对应一个独立MCP客户端实例，任务结束必须close，避免连接泄露</li>
 * <li>全链路多处检测任务取消标记，初始化、循环等待阶段均可被中断</li>
 * <li>初始化使用独立线程执行，避免阻塞主线程，设置超时截止时间</li>
 * </ul>
 * </p>
 */
public final class TaskScopedMcpTools implements AutoCloseable {
    /** MCP同步客户端实例，任务专属会话 */
    private final McpSyncClient client;
    /** 经过取消装饰器包装后的MCP工具回调列表，供Agent循环直接使用 */
    private final List<ToolCallback> callbacks;

    private TaskScopedMcpTools(McpSyncClient client, List<ToolCallback> callbacks) {
        this.client = client;
        this.callbacks = callbacks;
    }

    /**
     * 打开一个任务级别的MCP工具会话
     * <p>执行链路：取消校验 → 解析服务地址 → 构建WebClient与Transport → 创建McpSyncClient → initialize握手
     * → 获取远端工具列表并包装CancellationAwareToolCallback → 返回实例；发生异常会主动close客户端防止泄露。</p>
     *
     * @param serverUrl MCP服务端完整绝对URL地址
     * @param taskCapability Task‑Capability鉴权令牌，放入Authorization Bearer头
     * @param timeoutSeconds MCP initialize握手超时时间(秒)
     * @param cancelRequested 任务取消状态源，返回true代表任务需要终止
     * @return TaskScopedMcpTools 实例，使用完毕务必调用close()
     * @throws AgentExecutionCanceledException 检测到任务已取消时抛出
     * @throws IllegalArgumentException URL格式非法抛出
     * @throws RuntimeException MCP连接、握手失败抛出，内部会自动关闭client
     */
    public static TaskScopedMcpTools open(String serverUrl, String taskCapability,
                                          int timeoutSeconds, BooleanSupplier cancelRequested) {
        // 校验是否任务取消
        requireNotCanceled(cancelRequested);

        // 解析MCP服务baseUrl与path路径段
        McpEndpoint endpoint = McpEndpoint.from(serverUrl);

        // 构建WebClient，注入task‑capability鉴权Header
        WebClient.Builder webClient = WebClient.builder().baseUrl(endpoint.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        JwtConstant.TOKEN_TYPE_BEARER + " " + taskCapability);
        WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClient)
                .endpoint(endpoint.path()).build();

        McpSyncClient client = null;
        try {
            // 构建MCP同步客户端，设置客户端信息与请求超时
            client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation(AgentConstant.MCP_CLIENT_NAME,
                            AgentConstant.MCP_CLIENT_VERSION))
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds)).build();

            // 执行MCP initialize握手，带超时与取消检测
            initialize(client, timeoutSeconds, cancelRequested);
            requireNotCanceled(cancelRequested);

            // 获取远端MCP工具，全部包装任务取消装饰器
            List<ToolCallback> callbacks = Arrays.stream(new SyncMcpToolCallbackProvider(client)
                            .getToolCallbacks())
                    .map(callback -> new CancellationAwareToolCallback(callback, cancelRequested))
                    .map(ToolCallback.class::cast).toList();
            return new TaskScopedMcpTools(client, callbacks);
        } catch (RuntimeException exception) {
            // 异常场景主动关闭客户端，防止连接资源泄漏
            if (client != null) client.close();
            throw exception;
        }
    }

    /**
     * 获取已经包装好取消校验的MCP工具回调列表，直接供给Agent循环使用
     * @return 工具回调只读列表
     */
    public List<ToolCallback> callbacks() {
        return callbacks;
    }

    /**
     * AutoCloseable实现：关闭底层MCP客户端连接，释放HTTP流资源
     */
    @Override public void close() {
        client.close();
    }

    /**
     * 校验任务取消标记，已请求取消直接抛出异常中断流程
     * @param cancelRequested 取消状态查询源
     * @throws AgentExecutionCanceledException 任务已取消抛出
     */
    private static void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) throw new AgentExecutionCanceledException();
    }

    /**
     * 执行MCP initialize握手，运行在独立调度线程；支持超时、任务取消、线程中断
     * <p>
     * 不阻塞当前线程，循环轮询任务完成状态，每25ms检测一次取消标记与超时截止时间。
     * </p>
     *
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
        // 计算超时截止时间点（纳秒）
        Disposable scheduled = Schedulers.boundedElastic().schedule(task);
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        try {
            while (!task.isDone()) {
                requireNotCanceled(cancelRequested);
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("MCP 初始化超时");
                }
                Thread.sleep(25L);
            }
            // 获取执行结果，会透传任务内部异常
            task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 初始化被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("MCP 初始化失败", cause);
        } finally {
            // 未完成则强制取消任务，释放调度资源
            if (!task.isDone())
                task.cancel(true);
            scheduled.dispose();
        }
    }

    /**
     * MCP服务端地址解析记录类
     * <p>把传入的完整serverUrl拆分为 baseUrl(scheme+authority) 和 path(请求路径)。
     * path为空时使用配置的默认MCP端点路径。</p>
     *
     * @param baseUrl 协议+域名部分，例：http://127.0.0.1:8000
     * @param path MCP HTTP transport请求path路径段
     */
    private record McpEndpoint(String baseUrl, String path) {
        /**
         * 从完整绝对URL解析出McpEndpoint
         * @param serverUrl 用户传入MCP服务完整地址
         * @return 拆分后的McpEndpoint
         * @throws IllegalArgumentException 非绝对URL抛出
         */
        private static McpEndpoint from(String serverUrl) {
            URI uri = URI.create(serverUrl);
            if (uri.getScheme() == null || uri.getAuthority() == null)
                throw new IllegalArgumentException("MCP Server URL 必须是绝对地址");
            String path = uri.getRawPath();
            return new McpEndpoint(uri.getScheme() + "://" + uri.getAuthority(),
                    path == null || path.isBlank() ? AgentConstant.DEFAULT_MCP_ENDPOINT : path);
        }
    }
}
