package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.entity.BaseEntity;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.List;

/**
 * 模型适配器上下文
 * <p>
 * 传递给 {@link ModelAdapter} 的执行上下文对象，携带单次模型调用所需要的全部运行时信息：
 * Agent配置、模型实体、解密后的API密钥、单轮最大输出token、当前任务工具回调集合。
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *     <li>使用Java record，实例不可变；修改字段通过 {@code withXXX()} 方法返回全新上下文对象，防止并发修改；</li>
 *     <li>敏感字段 {@code apiKey} 在toString做脱敏处理，避免密钥打印到日志；</li>
 *     <li>提供 {@link #forConnectivityTest()} 生成连通性测试专用上下文，清空工具回调，限制输出token；</li>
 *     <li>所有传给ModelAdapter的入参统一封装在此对象，适配器不需要从外部零散拿参数。</li>
 * </ul>
 * <p>
 * ⚠️安全约束：apiKey为解密之后的明文密钥，只在内存流转；禁止直接打印、序列化输出原始密钥。
 */
public record ModelAdapterContext(
        AgentEntity agent,
        ModelEntity model,
        String apiKey,
        Integer maxOutputTokens,
        List<ToolCallback> toolCallbacks) {

    /**
     * 复制当前上下文，替换maxOutputTokens，返回全新的不可变上下文实例
     * @param value 新的单轮最大输出token限制
     * @return 新的ModelAdapterContext实例，其余字段复用原对象
     */
    public ModelAdapterContext withMaxOutputTokens(Integer value) {
        return new ModelAdapterContext(agent, model, apiKey, value, toolCallbacks);
    }

    /**
     * 复制当前上下文，替换toolCallbacks工具回调列表，返回全新的不可变上下文实例
     * @param value 新的工具回调集合
     * @return 新的ModelAdapterContext实例，其余字段复用原对象
     */
    public ModelAdapterContext withToolCallbacks(List<ToolCallback> value) {
        return new ModelAdapterContext(agent, model, apiKey, maxOutputTokens, value);
    }

    /**
     * 生成连通性测试专用上下文
     * <p>用于{@link ModelAdapter#testConnect}连通探测：清空工具回调，设置很小的maxOutputTokens；
     * 测试请求不需要真实执行工具调用。</p>
     * @return 连通性测试专用上下文副本
     */
    public ModelAdapterContext forConnectivityTest() {
        return new ModelAdapterContext(agent, model, apiKey, 1, Collections.emptyList());
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
