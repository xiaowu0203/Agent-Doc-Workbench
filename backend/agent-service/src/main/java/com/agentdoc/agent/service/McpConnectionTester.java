package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpConnectionStatus;
import com.agentdoc.agent.execution.tool.TaskScopedMcpTools;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.vo.McpToolVO;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static com.agentdoc.agent.constant.McpConstant.CONNECTION_TEST_TIMEOUT_SECONDS;
import static com.agentdoc.agent.constant.McpConstant.MAX_CONNECTION_TEST_ERROR_LENGTH;

/**
 * 执行 MCP 真实握手与工具发现。
 * <p>
 * 负责对MCP Server配置做连通性测试：地址安全校验、认证解密、建立MCP会话、拉取工具列表。
 * 连接异常不向上抛出，全部封装为测试结果对象返回，错误信息做脱敏、截断处理，避免敏感信息泄露与超长存储。
 */
@Component
@RequiredArgsConstructor
public class McpConnectionTester {

    /** 认证凭证加解密服务，用于解密存储的加密Token */
    private final AgentConfigCryptoService cryptoService;
    /** MCP端点安全校验器，校验外部地址合法性，防止危险内网/回环地址访问 */
    private final McpEndpointSecurityValidator endpointValidator;

    /**
     * 测试MCP服务连接并发现可用工具。
     * 连接失败作为可持久化结果返回，不向上抛出远端异常；会自动关闭MCP会话资源。
     * @param server MCP Server 配置快照，包含地址、认证类型、加密凭证等配置
     * @return 测试结果对象，包含状态、耗时、错误摘要、工具列表
     */
    public TestOutcome test(McpServerEntity server) {
        // 记录测试开始纳秒时间戳，用于计算连接耗时
        long startedNanos = System.nanoTime();
        String token = null;
        try {
            // 校验外部MCP服务端点地址安全合规
            endpointValidator.validateExternal(server.getEndpointUrl());
            // NONE不需要凭证；Bearer和Query API Key都从统一加密列解密
            token = McpAuthType.NONE.name().equals(server.getAuthType())
                    ? null : cryptoService.decrypt(server.getEncryptedAuthToken());
            String bearerToken = McpAuthType.BEARER.name().equals(server.getAuthType()) ? token : null;
            String queryParamValue = McpAuthType.QUERY_PARAM.name().equals(server.getAuthType()) ? token : null;

            // 开启Task作用域的MCP外部会话，try‑with‑resources保证会话自动关闭释放资源
            // 参数：服务地址、认证token、请求头、连接超时、取消判断回调、http自定义配置、解析后地址二次校验回调
            try (TaskScopedMcpTools session = TaskScopedMcpTools.openExternal(
                    server.getEndpointUrl(), bearerToken, server.getAuthParamName(), queryParamValue,
                    null, CONNECTION_TEST_TIMEOUT_SECONDS,
                    () -> false, null,
                    endpointValidator::validateResolved)) {
                // 从MCP会话回调中提取工具定义，转换为VO对象，按工具名称排序
                List<McpToolVO> tools = session.callbacks().stream()
                        .map(callback -> callback.getToolDefinition())
                        .map(definition -> new McpToolVO(definition.name(), definition.description(),
                                definition.inputSchema()))
                        .sorted(Comparator.comparing(McpToolVO::name))
                        .toList();
                // 连接成功：返回成功状态、测试时间、耗时、无错误信息、工具列表
                return new TestOutcome(McpConnectionStatus.SUCCESS, LocalDateTime.now(),
                        elapsedMillis(startedNanos), null, tools);
            }
        } catch (RuntimeException exception) {
            // 捕获所有运行时异常，封装为失败结果，不向上抛异常；工具列表返回空集合
            return new TestOutcome(McpConnectionStatus.FAILED, LocalDateTime.now(),
                    elapsedMillis(startedNanos), errorSummary(exception, token), List.of());
        }
    }

    /**
     * 计算从开始时间到当前的耗时，转换为毫秒。
     *
     * @param startedNanos 起始纳秒时间戳 {@link System#nanoTime()}
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /**
     * 提取异常根因摘要，做脱敏、空格压缩、长度截断处理。
     * <p>
     * 1. 遍历获取最内层根异常cause；
     * 2. 无异常消息时使用异常类名作为摘要；
     * 3. 将原始token替换为掩码 {@code <redacted>}，防止密钥泄露；
     * 4. 对超长错误信息做截断，满足数据库存储长度限制。
     *
     * @param exception 捕获到的运行时异常
     * @param token     解密后的Bearer Token，用于脱敏替换，可为null
     * @return 处理完成的安全错误摘要文本
     */
    private String errorSummary(RuntimeException exception, String token) {
        Throwable cause = exception;
        // 循环追溯最内层根异常，避免多层包装异常只拿到外层message
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        // 消息为空/空白时使用异常类名，否则压缩多个空白字符为单个空格并去除首尾空格
        String summary = message == null || message.isBlank()
                ? cause.getClass().getSimpleName() : message.replaceAll("\\s+", " ").trim();
        // 如果存在token，把token明文替换为脱敏标记，禁止密钥落库
        if (token != null && !token.isBlank()) {
            summary = summary.replace(token, "<redacted>");
        }
        // 超过最大错误长度则截断，防止数据库字段溢出
        return summary.length() <= MAX_CONNECTION_TEST_ERROR_LENGTH
                ? summary : summary.substring(0, MAX_CONNECTION_TEST_ERROR_LENGTH);
    }

    /**
     * 单次真实MCP连接测试结果记录。
     *
     * @param status      连接状态：成功 / 失败
     * @param testedAt    测试执行时间点
     * @param durationMs  测试耗时（毫秒）
     * @param errorMessage 错误摘要，成功时为null，已脱敏截断
     * @param tools       发现到的MCP工具列表，不可变集合；失败时为空列表
     */
    public record TestOutcome(McpConnectionStatus status, LocalDateTime testedAt, Long durationMs,
                              String errorMessage, List<McpToolVO> tools) {
        public TestOutcome {
            tools = List.copyOf(tools);
        }

        /**
         * 判断本次测试是否连接成功。
         *
         * @return true=连接握手与工具发现成功；false=连接失败
         */
        public boolean connected() {
            return status == McpConnectionStatus.SUCCESS;
        }
    }
}
