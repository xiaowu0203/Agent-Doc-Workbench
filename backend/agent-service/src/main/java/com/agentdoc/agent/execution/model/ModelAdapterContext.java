package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.execution.context.ExecutionSnapshotCopies;
import com.agentdoc.common.pojo.entity.BaseEntity;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.List;

/**
 * 模型适配器上下文 record
 * <p>
 * 封装调用大模型适配器所需全部运行时参数，作为模型调用的入参上下文在链路中传递。
 * 核心设计：不可变对象；构造阶段对 agent、model 实体做快照拷贝、工具回调列表做不可变封装，
 * 避免外部修改原始实体/集合污染上下文内部状态；所有withXXX方法均返回全新副本，不修改原实例。
 * </p>
 *
 * @param agent          Agent实体快照（已拷贝，非原始DB托管实体）
 * @param model          模型配置快照（已拷贝，非原始DB托管实体）
 * @param apiKey         模型服务商密钥，敏感信息，toString会脱敏不输出明文
 * @param maxOutputTokens 单轮模型最大输出token
 * @param temperature    温度参数，控制模型随机性，可为null，使用模型默认值
 * @param topP           Top-P采样率，可为null，使用模型默认值
 * @param toolCallbacks  工具回调定义列表，构造后转为不可变集合
 * @param executionId    Agent执行任务ID，归属哪一次执行实例，可为null
 */
public record ModelAdapterContext(
        AgentEntity agent,
        ModelEntity model,
        String apiKey,
        Integer maxOutputTokens,
        Double temperature,
        Double topP,
        List<ToolCallback> toolCallbacks,
        Long executionId) {

    public ModelAdapterContext {
        agent = ExecutionSnapshotCopies.agent(agent);
        model = ExecutionSnapshotCopies.model(model);
        toolCallbacks = List.copyOf(toolCallbacks);
    }

    /**
     * agent 访问器重写：再次返回一份快照副本
     * <p>
     * 虽然构造已经拷贝一次；再次拷贝防止调用方拿到内部引用后对实体setter修改。
     * </p>
     * @return agent实体快照副本
     */
    @Override
    public AgentEntity agent() {
        return ExecutionSnapshotCopies.agent(agent);
    }

    /**
     * model 访问器重写：再次返回一份快照副本
     * @return model实体快照副本
     */
    @Override
    public ModelEntity model() {
        return ExecutionSnapshotCopies.model(model);
    }

    public ModelAdapterContext(AgentEntity agent, ModelEntity model, String apiKey,
                               Integer maxOutputTokens, List<ToolCallback> toolCallbacks) {
        this(agent, model, apiKey, maxOutputTokens, null, null, toolCallbacks, null);
    }

    public ModelAdapterContext(AgentEntity agent, ModelEntity model, String apiKey,
                               Integer maxOutputTokens, Double temperature, List<ToolCallback> toolCallbacks) {
        this(agent, model, apiKey, maxOutputTokens, temperature, null, toolCallbacks, null);
    }

    public ModelAdapterContext(AgentEntity agent, ModelEntity model, String apiKey,
                               Integer maxOutputTokens, Double temperature, Double topP,
                               List<ToolCallback> toolCallbacks) {
        this(agent, model, apiKey, maxOutputTokens, temperature, topP, toolCallbacks, null);
    }

    /**
     * 复制当前上下文，替换maxOutputTokens，返回全新的不可变上下文实例
     * @param value 新的单轮最大输出token限制
     * @return 新的ModelAdapterContext实例，其余字段复用原对象
     */
    public ModelAdapterContext withMaxOutputTokens(Integer value) {
        return new ModelAdapterContext(agent, model, apiKey, value, temperature, topP, toolCallbacks, executionId);
    }

    /**
     * 复制当前上下文，替换toolCallbacks工具回调列表，返回全新的不可变上下文实例
     * @param value 新的工具回调集合
     * @return 新的ModelAdapterContext实例，其余字段复用原对象
     */
    public ModelAdapterContext withToolCallbacks(List<ToolCallback> value) {
        return new ModelAdapterContext(agent, model, apiKey, maxOutputTokens, temperature, topP, value, executionId);
    }

    /**
     * 复制当前上下文，替换temperature温度参数，返回全新的不可变上下文实例
     * @param value 新的temperature值
     * @return 新的ModelAdapterContext实例，其余字段复用原对象
     */
    public ModelAdapterContext withTemperature(Double value) {
        return new ModelAdapterContext(agent, model, apiKey, maxOutputTokens, value, topP, toolCallbacks, executionId);
    }

    /**
     * 复制当前上下文，替换Top-P采样率。
     */
    public ModelAdapterContext withTopP(Double value) {
        return new ModelAdapterContext(agent, model, apiKey, maxOutputTokens, temperature, value,
                toolCallbacks, executionId);
    }

    /**
     * 复制当前上下文，设置执行任务ID，返回全新的不可变上下文实例
     * @param value Agent执行实例ID
     * @return 新的ModelAdapterContext实例，其余字段复用原对象
     */
    public ModelAdapterContext withExecutionId(Long value) {
        return new ModelAdapterContext(agent, model, apiKey, maxOutputTokens, temperature, topP,
                toolCallbacks, value);
    }

    /**
     * 生成连通性测试专用上下文
     * <p>用于{@link ModelAdapter#testConnect}连通探测：清空工具回调，设置很小的maxOutputTokens；
     * 测试请求不需要真实执行工具调用。</p>
     * @return 连通性测试专用上下文副本
     */
    public ModelAdapterContext forConnectivityTest() {
        return new ModelAdapterContext(agent, model, apiKey, 1, temperature, topP,
                Collections.emptyList(), null);
    }

    /**
     * 重写toString，做敏感信息脱敏
     * <p>apiKey明文不输出，替换为{@literal <redacted>}，防止密钥泄露到日志。</p>
     * @return 脱敏后的上下文描述字符串
     */
    @Override
    public String toString() {
        return "ModelAdapterContext[agentId=" + idOf(agent) +
                ", modelId=" + idOf(model) +
                ", apiKey=" + (apiKey == null ? "null" : "<redacted>") +
                ", maxOutputTokens=" + maxOutputTokens +
                ", temperature=" + temperature +
                ", topP=" + topP +
                ", executionId=" + executionId +
                ", toolCallbackCount=" + (toolCallbacks == null ? 0 : toolCallbacks.size()) + "]";
    }

    /**
     * 提取实体ID，如果对象是BaseEntity子类则返回id，否则返回null
     * @param entity AgentEntity / ModelEntity 实体对象
     * @return 实体主键id，非BaseEntity返回null
     */
    private Long idOf(Object entity) {
        if (entity instanceof BaseEntity baseEntity) {
            return baseEntity.getId();
        }
        return null;
    }
}
