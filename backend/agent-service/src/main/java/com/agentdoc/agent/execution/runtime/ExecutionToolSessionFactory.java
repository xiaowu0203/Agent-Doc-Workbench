package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.execution.tool.CancellationAwareToolCallback;
import com.agentdoc.agent.service.SkillResourceLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class ExecutionToolSessionFactory {
    /** resource_list 工具入参JSON Schema：接收skillVersionId */
    private static final String LIST_SCHEMA = "{\"type\":\"object\",\"properties\":"
            + "{\"skillVersionId\":{\"type\":\"integer\"}}}";
    /** resource_read 工具入参JSON Schema：接收skillVersionId与资源path，两个字段必填 */
    private static final String READ_SCHEMA = "{\"type\":\"object\",\"properties\":"
            + "{\"skillVersionId\":{\"type\":\"integer\"},"
            + "\"path\":{\"type\":\"string\"}},"
            + "\"required\":[\"skillVersionId\",\"path\"]}";

    /** Skill资源加载器，负责读取Skill版本内的可读文本资源 */
    private final SkillResourceLoader resourceLoader;

    /** JSON序列化反序列化工具 */
    private final ObjectMapper objectMapper;

    public ExecutionToolSessionFactory(SkillResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
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
        TaskScopedMcpTools mcp = null;
        try {
            // 打开任务隔离MCP会话，传入MCP服务地址、任务能力、超时、取消信号、允许MCP工具白名单
            mcp = TaskScopedMcpTools.open(
                    context.taskInput().mcpServerUrl(),
                    context.taskInput().taskCapability(),
                    timeoutSeconds(context),
                    cancelRequested,
                    context.allowedMcpTools()
            );
            // 复制MCP远端返回的全部回调，后续追加Skill本地资源工具回调
            List<ToolCallback> callbacks = new ArrayList<>(mcp.callbacks());

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
                if (callbacks.stream().anyMatch(value -> value.getToolDefinition().name().equals(SkillConstant.RESOURCE_LIST_TOOL)
                        || value.getToolDefinition().name().equals(SkillConstant.RESOURCE_READ_TOOL))) {
                    throw new IllegalStateException("远端 MCP 工具名称与 Skill 资源工具冲突");
                }
                // 注册列出Skill资源本地工具，包装取消信号感知
                callbacks.add(new CancellationAwareToolCallback(listCallback(resourceMap), cancelRequested));
                // 注册读取Skill文本资源本地工具，包装取消信号感知
                callbacks.add(new CancellationAwareToolCallback(readCallback(textResourceMap), cancelRequested));
            }
            // 返回不可修改的回调列表，构造工具会话
            return new ExecutionToolSession(mcp, List.copyOf(callbacks));
        } catch (RuntimeException exception) {
            // 异常场景安全释放MCP会话，防止连接泄漏
            if (mcp != null) {
                mcp.close();
            }
            throw exception;
        }
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
                    return writeJson(selected.values().stream()
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
     * 解析工具调用输入JSON为Map，异常兜底返回空Map
     *
     * @param input 工具原始入参JSON字符串，允许null
     * @return 参数Map；解析失败返回空Map
     */
    private Map<String, Object> parse(String input) {
        try {
            return objectMapper.readValue(input == null ? "{}" : input,
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            return Map.of();
        }
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
     * 对象序列化为JSON字符串；异常兜底返回"[]"
     *
     * @param value 待序列化对象
     * @return JSON字符串，序列化失败返回"[]"
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
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
