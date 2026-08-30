package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.agent.execution.audit.AgentExecutionToolAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 带审计能力的工具回调包装器
 * <p>
 * 装饰器模式，对原始{@link ToolCallback}做包装增强；**不修改真实工具调用逻辑，在工具调用前后插入审计落库逻辑**。
 * 负责记录每一次工具调用的开始、成功、失败完整审计记录：入参、出参hash、长度、调用序号、来源、MCP服务标识。
 * <p>
 * 审计自身异常做隔离保护：
 * 1. 工具业务调用成功，但审计succeed落库异常：仅打印error日志，不打断工具正常返回结果；
 * 2. 工具业务调用抛出异常，审计fail落库异常：仅打印error日志，继续向外抛出原始业务异常，不吞业务报错；
 * 保证审计模块故障不会造成Agent工具执行流程中断。
 * </p>
 */
@Slf4j
public class AuditingToolCallback implements ToolCallback {
    /**
     * 被包装的原始工具回调委托对象，真实执行工具call逻辑
     */
    private final ToolCallback delegate;

    /**
     * Agent执行实例ID，一次Agent会话执行唯一ID，审计记录外键
     */
    private final Long executionId;

    /**
     * 调用来源类型，用于区分工具来源：如SKILL / MCP等
     */
    private final String source;

    /**
     * 来源唯一key，例如skillVersionId字符串、mcp工具标识
     */
    private final String sourceKey;

    /**
     * MCP服务实例ID，如果是Skill工具可为null
     */
    private final Long mcpServerId;

    /**
     * 工具调用序列号生成器，同一个executionId内全局自增，标记本次执行内第N次工具调用；
     * 外部传入AtomicInteger，保证多个AuditingToolCallback共享同一个序号计数器，序号连续不重复
     */
    private final AtomicInteger sequence;

    /**
     * Agent工具调用审计服务，负责审计记录的开始、成功、失败数据库持久化
     */
    private final AgentExecutionToolAuditService auditService;

    /**
     * 构造审计包装回调
     * @param delegate 原始工具回调委托
     * @param executionId Agent执行会话ID
     * @param source 工具来源分类
     * @param sourceKey 来源唯一标识key
     * @param mcpServerId MCP服务ID，非MCP工具传null
     * @param sequence 共享的序号自增计数器，同一个executionId复用同一个实例
     * @param auditService 工具审计持久化服务
     */
    public AuditingToolCallback(ToolCallback delegate, Long executionId, String source,
                                String sourceKey, Long mcpServerId,
                                AtomicInteger sequence, AgentExecutionToolAuditService auditService) {
        this.delegate = delegate;
        this.executionId = executionId;
        this.source = source;
        this.sourceKey = sourceKey;
        this.mcpServerId = mcpServerId;
        this.sequence = sequence;
        this.auditService = auditService;
    }

    /**
     * 获取工具定义，直接委托原始实现
     * @return 工具定义元数据
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    /**
     * 获取工具元数据，直接委托原始实现
     * @return 工具元数据
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * 无ToolContext重载工具调用入口，走统一审计模板方法
     * @param input 工具入参JSON字符串
     * @return 工具执行返回结果
     */
    @Override
    public String call(String input) {
        return audited(input, delegate::call);
    }

    /**
     * 携带ToolContext上下文工具调用入口，走统一审计模板方法
     * @param input 工具入参JSON字符串
     * @param toolContext 工具运行上下文
     * @return 工具执行返回结果
     */
    @Override
    public String call(String input, ToolContext toolContext) {
        return audited(input, value -> delegate.call(value, toolContext));
    }

    /**
     * 核心审计模板方法：统一封装【审计开始 → 真实工具调用 → 成功审计/失败审计】完整流程
     * <p>流程：
     * 1. 将入参转为UTF‑8字节数组，计算入参SHA‑256、入参字节长度；
     * 2. auditService.start() 创建审计记录，状态标记为进行中，分配sequence调用序号；
     * 3. 执行真实工具调用invocation.apply(input)；
     * 4. 工具调用成功：计算返回结果hash和长度，调用auditService.succeed更新审计记录为成功；
     *    ——> 如果succeed审计落库异常，仅打error日志，不影响返回工具结果；
     * 5. 工具调用抛出RuntimeException：调用auditService.fail更新审计记录为失败；
     *    ——> 如果fail审计落库异常，仅打error日志，继续向外抛出原始业务异常，不掩盖工具报错；
     * </p>
     * @param input 工具调用入参
     * @param invocation 委托执行真实工具调用的函数式接口，兼容两个call重载方法
     * @return 工具原始返回结果字符串
     */
    private String audited(String input, Function<String, String> invocation) {
        // 入参转UTF‑8字节，null转为空字符串字节数组
        byte[] arguments = bytes(input);

        // 创建审计记录：sequence自增拿到本次调用序号，记录入参hash、入参字节大小
        AgentExecutionToolCallEntity audit = auditService.start(executionId, sequence.incrementAndGet(),
                getToolDefinition().name(), source, sourceKey, mcpServerId,
                sha256(arguments), arguments.length);

        try {
            // 执行真实工具调用逻辑
            String result = invocation.apply(input);
            byte[] resultBytes = bytes(result);
            try {
                // 工具调用业务成功，更新审计记录：出参hash、出参长度，状态置成功
                auditService.succeed(audit, sha256(resultBytes), resultBytes.length);
            } catch (RuntimeException exception) {
                // 审计落库失败属于次要故障，只打印错误日志，不阻断工具正常返回
                log.error("工具调用已成功但结束审计失败: executionId={}, auditId={}, tool={}",
                        executionId, audit.getId(), getToolDefinition().name(), exception);
            }
            return result;
        } catch (RuntimeException exception) {
            try {
                // 工具执行业务抛出异常，标记审计记录为失败，记录异常简单类名
                auditService.fail(audit, exception.getClass().getSimpleName());
            } catch (RuntimeException auditException) {
                // 审计fail自身异常，打印日志；原始工具异常继续上抛，不能吞掉业务错误
                log.error("工具调用失败且结束审计失败: executionId={}, auditId={}, tool={}",
                        executionId, audit.getId(), getToolDefinition().name(), auditException);
            }
            throw exception;
        }
    }

    /**
     * 字符串转UTF‑8字节数组；null安全处理，null输出空字符串字节
     * @param value 原始字符串，可以为null
     * @return UTF‑8字节数组
     */
    private byte[] bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 计算字节数组SHA‑256十六进制小写哈希；用于审计记录输入输出完整性校验
     * @param value 待计算字节数组
     * @return sha256十六进制字符串
     */
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
