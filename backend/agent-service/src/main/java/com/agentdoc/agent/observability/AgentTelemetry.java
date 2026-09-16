package com.agentdoc.agent.observability;

import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.skill.SkillSelectionResult;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.pojo.TokenValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Agent 领域遥测入口。仅创建业务边界 Span，HTTP、模型和工具技术 Span 由现有框架或 Java Agent 负责。
 * <p>
 * 基于 OpenTelemetry 实现 Agent 业务链路埋点；遵循 GenAI Semantic Conventions 规范。
 * 只负责业务域的粗粒度边界 Span，底层网络、http、基础模型通信等技术层埋点交由 OTel Agent/框架自动采集。
 * 内部提供 ModelCallSpan / ToolCallSpan 句柄，用于手动管理模型调用、MCP工具调用Span生命周期，避免忘记end。
 * 埋点原则：不记录提示词、返回报文、密钥、凭证等敏感内容；仅记录ID、计数、状态、token用量、错误类型等元数据。
 * </p>
 */
@Component
public class AgentTelemetry {
    /** 埋点 instrumentation scope，用于在遥测后端区分埋点来源 */
    private static final String INSTRUMENTATION_SCOPE = "com.agentdoc.agent";
    /** Agent主执行任务span名称，代表一次Agent任务完整业务边界 */
    private static final String EXECUTE_SPAN_NAME = "agentdoc.agent.execute";
    /** GenAI规范模型调用span名称，遵循OpenTelemetry GenAI语义约定 */
    private static final String MODEL_CALL_SPAN_NAME = "gen_ai.client.operation";
    /** MCP外部工具执行span名称 */
    private static final String MCP_TOOL_SPAN_NAME = "agentdoc.mcp.tool.execute";
    /** MCP服务连接初始化span名称 */
    private static final String MCP_CONNECT_SPAN_NAME = "agentdoc.mcp.connect";
    /** Skill候选选择span名称 */
    private static final String SKILL_SELECT_SPAN_NAME = "agentdoc.skill.select";
    /** 本地Skill读取加载span名称 */
    private static final String SKILL_READ_SPAN_NAME = "agentdoc.skill.read";
    /** GenAI语义规范版本号，用于遥测平台识别属性规范 */
    public static final String GEN_AI_SEMCONV_VERSION = "1.41.1";
    /** 字符串属性最大长度限制，防止超长字段污染遥测存储 */
    private static final int MAX_TECHNICAL_NAME_LENGTH = 128;

    private final Tracer tracer;

    /**
     * 默认构造器，使用全局OpenTelemetry获取Tracer，Spring自动注入
     */
    public AgentTelemetry() {
        this(GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE));
    }

    /**
     * 包私有构造，用于单元测试注入自定义tracer
     * @param tracer OpenTelemetry Tracer实例
     */
    AgentTelemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 在 Agent 执行业务边界内运行操作。
     * <p>
     * 创建Agent主执行Span，绑定任务、空间、Agent标识；自动激活scope，操作完成自动结束span。
     * 捕获运行时异常：标记span失败状态，仅记录异常类型名称，不记录异常message/堆栈；异常继续向上抛出。
     * </p>
     * @param input Agent任务入参DTO，携带workbenchTaskId、spaceId、agentId
     * @param operation 待执行的Agent业务逻辑
     */
    public void execute(AgentTaskInputDTO input, Runnable operation) {
        Span span = tracer.spanBuilder(EXECUTE_SPAN_NAME)
                .setAttribute("run.id", input.workbenchTaskId())
                .setAttribute("agentdoc.space.id", input.spaceId())
                .setAttribute("agentdoc.agent.id", input.agentId())
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            operation.run();
        } catch (RuntimeException exception) {
            markFailed(exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    /**
     * 主执行记录建立后补齐执行维度属性。
     * <p>
     * 在已激活的当前Span上追加executionId、agent配置版本，execute创建span后业务执行中途调用。
     * </p>
     * @param execution Agent执行记录实体
     */
    public void bindExecution(AgentExecutionEntity execution) {
        Span span = Span.current();
        if (execution.getId() != null) {
            span.setAttribute("execution.id", execution.getId());
        }
        if (execution.getAgentConfigVersion() != null) {
            span.setAttribute("agentdoc.agent.config_version", execution.getAgentConfigVersion());
        }
    }

    /**
     * 建立一次真实模型请求的领域 Span。调用方必须在请求执行期间激活并最终结束返回的句柄。
     * <p>
     * Span类型为CLIENT，遵循genAI语义约定；绑定模型提供商、模型key、序号、流式标记、executionId。
     * 返回ModelCallSpan句柄，由调用方在模型请求结束后手动调用succeed/fail/cancel完成span生命周期。
     * </p>
     * @param context 模型适配器上下文
     * @param sequence 本次模型调用序号，区分多轮调用
     * @param streaming 是否流式模型调用
     * @param runtimeType 运行时类型标识
     * @return ModelCallSpan 模型调用span生命周期句柄
     */
    public ModelCallSpan startModelCall(ModelAdapterContext context, int sequence,
                                        boolean streaming, String runtimeType) {
        SpanBuilder spanBuilder = tracer.spanBuilder(MODEL_CALL_SPAN_NAME)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("agentdoc.model.call.sequence", sequence)
                .setAttribute("agentdoc.model.stream", streaming);
        var model = context.model();
        if (model != null) {
            setAttribute(spanBuilder, "gen_ai.provider.name", model.getProvider());
            setAttribute(spanBuilder, "gen_ai.request.model", model.getModelKey());
        }
        setAttribute(spanBuilder, "agentdoc.runtime.type", runtimeType);
        if (context.executionId() != null) {
            spanBuilder.setAttribute("execution.id", context.executionId());
        }
        return new ModelCallSpan(spanBuilder.startSpan());
    }

    /**
     * 建立一次 MCP 工具执行或 Skill 渐进读取的领域 Span。
     * <p>
     * 根据source区分：SKILL_LOCAL 走Skill读取span，其余走MCP工具执行span，Span类型INTERNAL。
     * 外部MCP会记录mcpServerId；本地Skill记录skillVersionId。
     * 返回ToolCallSpan句柄，由调用方手动完成span生命周期。
     * </p>
     * @param executionId Agent执行记录ID
     * @param technicalName 工具技术名称
     * @param source 来源标识：SKILL_LOCAL / WORKBENCH / EXTERNAL_MCP
     * @param mcpServerId MCP服务ID，外部MCP非空
     * @param skillVersionId Skill版本ID，仅本地Skill读取场景有效
     * @return ToolCallSpan 工具调用span生命周期句柄
     */
    public ToolCallSpan startToolCall(Long executionId, String technicalName, String source,
                                      Long mcpServerId, Long skillVersionId) {
        boolean skillRead = "SKILL_LOCAL".equals(source);
        SpanBuilder spanBuilder = tracer.spanBuilder(skillRead ? SKILL_READ_SPAN_NAME : MCP_TOOL_SPAN_NAME)
                .setSpanKind(SpanKind.INTERNAL);
        if (executionId != null) {
            spanBuilder.setAttribute("execution.id", executionId);
        }
        if (skillRead) {
            if (skillVersionId != null) {
                spanBuilder.setAttribute("agentdoc.skill.version_id", skillVersionId);
            }
        } else {
            setAttribute(spanBuilder, "agentdoc.tool.technical_name", technicalName);
            spanBuilder.setAttribute("agentdoc.mcp.external", mcpServerId != null);
            spanBuilder.setAttribute("agentdoc.tool.source",
                    mcpServerId == null ? "WORKBENCH" : "EXTERNAL_MCP");
            if (mcpServerId != null) {
                spanBuilder.setAttribute("agentdoc.mcp.server_id", mcpServerId);
            }
        }
        return new ToolCallSpan(spanBuilder.startSpan());
    }

    /**
     * 记录 Skill 候选选择边界，仅保留模式和数量，不记录指令或 Skill 正文。
     * <p>
     * 执行Skill候选筛选逻辑；span记录候选数量、选择模式，执行完成后更新实际选中数量与effectiveMode。
     * 异常场景标记span失败，仅记录异常类型，不保存异常详情。
     * </p>
     * @param selectionMode 原始选择模式
     * @param candidateCount 候选Skill总数
     * @param operation Skill选择逻辑，返回选择结果
     * @return SkillSelectionResult 选择结果
     */
    public SkillSelectionResult selectSkills(String selectionMode, int candidateCount,
                                             Supplier<SkillSelectionResult> operation) {
        SpanBuilder spanBuilder = tracer.spanBuilder(SKILL_SELECT_SPAN_NAME)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("agentdoc.skill.candidate_count", candidateCount);
        setAttribute(spanBuilder, "agentdoc.skill.selection_mode", selectionMode);
        Span span = spanBuilder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            SkillSelectionResult result = operation.get();
            setAttribute(span, "agentdoc.skill.selection_mode", result.effectiveMode());
            span.setAttribute("agentdoc.skill.selected_count", result.selectedSkills().size());
            span.setAttribute("agentdoc.operation.status", "completed");
            return result;
        } catch (RuntimeException exception) {
            markFailed(span, exception.getClass().getName());
            throw exception;
        } finally {
            span.end();
        }
    }

    /**
     * 记录一次任务级 MCP 会话初始化，不记录端点、认证配置或凭证。
     * <p>
     * 创建MCP连接建立span，区分内置工作台MCP与外部MCP服务；仅记录ID、来源标记，不记录地址/密钥。
     * </p>
     * @param executionId Agent执行记录ID
     * @param mcpServerId MCP服务ID，外部MCP非空
     * @param operation MCP连接初始化逻辑
     * @param <T> 返回结果泛型
     * @return 连接操作返回结果
     */
    public <T> T connectMcp(Long executionId, Long mcpServerId, Supplier<T> operation) {
        boolean external = mcpServerId != null;
        SpanBuilder spanBuilder = tracer.spanBuilder(MCP_CONNECT_SPAN_NAME)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("agentdoc.mcp.external", external)
                .setAttribute("agentdoc.tool.source", external ? "EXTERNAL_MCP" : "WORKBENCH");
        if (executionId != null) {
            spanBuilder.setAttribute("execution.id", executionId);
        }
        if (external) {
            spanBuilder.setAttribute("agentdoc.mcp.server_id", mcpServerId);
        }
        Span span = spanBuilder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            T result = operation.get();
            span.setAttribute("agentdoc.operation.status", "completed");
            return result;
        } catch (RuntimeException exception) {
            markFailed(span, exception.getClass().getName());
            throw exception;
        } finally {
            span.end();
        }
    }

    /**
     * 记录受控失败分类，不把异常 message 或 stacktrace 写入 Span。
     * <p>
     * 读取当前活跃Span，标记状态failed、error.type；只保存异常类名，规避敏感信息泄露。
     * </p>
     * @param exception 运行时异常
     */
    public void markFailed(RuntimeException exception) {
        markFailed(exception.getClass().getName());
    }

    /**
     * 标记当前活跃Span为失败，传入错误类型名称。
     * @param errorType 错误类型标识（异常类名）
     */
    public void markFailed(String errorType) {
        markFailed(Span.current(), errorType);
    }

    /**
     * 私有辅助：给指定span打上失败状态与error属性，不记录异常消息。
     * @param span 目标span
     * @param errorType 错误类型名称
     */
    private static void markFailed(Span span, String errorType) {
        span
                .setAttribute("agentdoc.operation.status", "failed")
                .setAttribute("error.type", errorType)
                .setStatus(StatusCode.ERROR);
    }

    /**
     * 记录取消终态，标记当前活跃span为canceled。
     */
    public void markCanceled() {
        Span.current().setAttribute("agentdoc.operation.status", "canceled");
    }

    /**
     * 记录成功终态，标记当前活跃span为completed。
     */
    public void markCompleted() {
        Span.current().setAttribute("agentdoc.operation.status", "completed");
    }

    /**
     * 向SpanBuilder设置字符串属性，增加空、空白、长度上限校验，避免脏数据。
     * @param spanBuilder span构建器
     * @param key 属性名
     * @param value 属性值
     */
    private static void setAttribute(SpanBuilder spanBuilder, String key, String value) {
        if (value != null && !value.isBlank() && value.length() <= MAX_TECHNICAL_NAME_LENGTH) {
            spanBuilder.setAttribute(key, value);
        }
    }

    /**
     * 向已启动Span设置字符串属性，增加空、空白、长度上限校验。
     * @param span 已启动span
     * @param key 属性名
     * @param value 属性值
     */
    private static void setAttribute(Span span, String key, String value) {
        if (value != null && !value.isBlank() && value.length() <= MAX_TECHNICAL_NAME_LENGTH) {
            span.setAttribute(key, value);
        }
    }

    /**
     * 单次模型请求 Span 的显式生命周期句柄。
     * <p>
     * 模型调用span生命周期交由调用方手动控制；用AtomicBoolean防止多次end。
     * 支持成功（带回TokenUsage）、失败、取消三种终态；自动上报genai token用量与用量来源（实际/估算/缺失）。
     * </p>
     */
    public static final class ModelCallSpan {
        private final Span span;
        /** 标记span是否已经结束，防止重复end */
        private final AtomicBoolean ended = new AtomicBoolean();

        private ModelCallSpan(Span span) {
            this.span = span;
        }

        /**
         * 激活当前span到上下文Scope，返回scope用于try-with-resources关闭。
         * @return OpenTelemetry Scope
         */
        public Scope makeCurrent() {
            return span.makeCurrent();
        }

        /**
         * 模型调用成功结束，写入token用量并关闭span。
         * @param usage token消耗数据
         */
        public void succeed(TokenUsage usage) {
            if (!ended.compareAndSet(false, true)) return;
            setUsage(usage);
            span.setAttribute("agentdoc.operation.status", "completed");
            span.end();
        }

        /**
         * 模型调用异常失败，标记error.type，关闭span。
         * @param exception 捕获的异常
         */
        public void fail(Throwable exception) {
            if (!ended.compareAndSet(false, true)) return;
            span.setAttribute("agentdoc.operation.status", "failed");
            span.setAttribute("error.type", exception.getClass().getName());
            span.setStatus(StatusCode.ERROR);
            span.end();
        }

        /**
         * 模型调用被取消，记录token用量并标记canceled，关闭span。
         * @param usage token消耗数据
         */
        public void cancel(TokenUsage usage) {
            if (!ended.compareAndSet(false, true)) return;
            setUsage(usage);
            span.setAttribute("agentdoc.operation.status", "canceled");
            span.end();
        }

        /**
         * 填充token用量属性，包含输入、输出token，以及用量来源标记。
         * @param usage token使用对象
         */
        private void setUsage(TokenUsage usage) {
            if (usage == null) return;
            setToken("gen_ai.usage.input_tokens", usage.input());
            setToken("gen_ai.usage.output_tokens", usage.output());
            span.setAttribute("agentdoc.model.usage.source", usageSource(usage));
        }

        /**
         * 写入单个token指标，仅在TokenValue可用时设置属性。
         * @param key 属性key
         * @param value token数值包装对象
         */
        private void setToken(String key, TokenValue value) {
            if (value != null && value.available()) {
                span.setAttribute(key, value.value());
            }
        }

        /**
         * 判断token数据来源类型：ACTUAL真实计费值 / ESTIMATED估算值 / MISSING数据缺失。
         * @param usage token用量对象
         * @return 来源标识字符串
         */
        private String usageSource(TokenUsage usage) {
            if (!usage.input().available() || !usage.output().available()) return "MISSING";
            if (usage.input().estimated() || usage.output().estimated()) return "ESTIMATED";
            return "ACTUAL";
        }
    }

    /**
     * 单次工具调用 Span 的显式生命周期句柄。
     * <p>
     * MCP工具/本地Skill读取span生命周期手动管理；AtomicBoolean防重复end。
     * 成功时上报返回结果字节大小；失败仅记录异常类型名称，不记录异常详情。
     * </p>
     */
    public static final class ToolCallSpan {
        private final Span span;
        /** 标记span是否已经结束，防止重复end */
        private final AtomicBoolean ended = new AtomicBoolean();

        private ToolCallSpan(Span span) {
            this.span = span;
        }

        /**
         * 激活当前span到上下文Scope，返回scope用于try-with-resources关闭。
         * @return OpenTelemetry Scope
         */
        public Scope makeCurrent() {
            return span.makeCurrent();
        }

        /**
         * 工具调用执行成功，记录返回结果大小，关闭span。
         * @param resultSizeBytes 返回结果字节数
         */
        public void succeed(long resultSizeBytes) {
            if (!ended.compareAndSet(false, true)) return;
            span.setAttribute("agentdoc.tool.result_type", "STRING");
            span.setAttribute("agentdoc.tool.result_size_bytes", resultSizeBytes);
            span.setAttribute("agentdoc.operation.status", "completed");
            span.end();
        }

        /**
         * 工具调用失败，自动提取异常类名作为errorType。
         * @param exception 捕获异常
         */
        public void fail(Throwable exception) {
            fail(exception.getClass().getName());
        }

        /**
         * 工具调用失败，传入自定义errorType标记，关闭span。
         * @param errorType 错误类型标识
         */
        public void fail(String errorType) {
            if (!ended.compareAndSet(false, true)) return;
            span.setAttribute("agentdoc.operation.status", "failed");
            span.setAttribute("error.type", errorType);
            span.setStatus(StatusCode.ERROR);
            span.end();
        }
    }
}
