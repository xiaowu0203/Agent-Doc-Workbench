package com.agentdoc.agent.execution.runtime.alibaba;

import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.tool.TokenUsageEstimator;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Spring‑AI Alibaba桥接运行时Token用量收集器
 * <p>
 * 接收底层模型返回的{@link ChatResponse}，通过{@link ModelAdapter}提取原始Token用量；
 * 再调用Token估算器对缺失的用量字段做估算补齐，之后把组装完成的{@link TokenUsage}交给{@link AlibabaRuntimeControl}做统计与校验。
 * 区分正常完成 / 任务取消两种场景，分别回调control对应方法。
 * </p>
 */
public final class AlibabaRuntimeUsageCollector {

    /** 桥接层控制器，负责统计、熔断、取消检测 */
    private final AlibabaRuntimeControl control;
    /** 模型适配器，用于从ChatResponse解析原始token用量 */
    private final ModelAdapter adapter;

    /**
     * @param control 桥接运行时控制器
     * @param adapter 模型适配器SPI
     */
    public AlibabaRuntimeUsageCollector(AlibabaRuntimeControl control, ModelAdapter adapter) {
        this.control = control;
        this.adapter = adapter;
    }

    /**
     * 模型调用正常结束，接收响应与上下文消息、工具列表，处理用量统计
     * @param response Spring‑AI原始模型响应
     * @param messages 当前轮次上下文消息列表
     * @param tools 本次使用的工具回调集合
     */
    public void accept(ChatResponse response, List<Message> messages, List<ToolCallback> tools) {
        accept(response, messages, tools, false);
    }

    /**
     * 模型调用在中途被取消时调用，记录用量但跳过后续部分业务校验
     * @param response 模型返回响应
     * @param messages 当前轮次上下文消息列表
     * @param tools 本次使用的工具回调集合
     */
    public void acceptAfterCancellation(ChatResponse response, List<Message> messages, List<ToolCallback> tools) {
        accept(response, messages, tools, true);
    }

    /**
     * 内部统一处理逻辑：提取原始用量 → 估算补齐缺失字段 → 交由control做后续处理
     * @param response 底层模型响应
     * @param messages 上下文消息
     * @param tools 工具回调列表
     * @param canceled 是否为取消场景
     */
    private void accept(ChatResponse response, List<Message> messages, List<ToolCallback> tools,
                        boolean canceled) {
        // 通过ModelAdapter从原始ChatResponse提取厂商返回的原始token用量
        TokenUsage raw = adapter.tokenUsage(response);
        // 使用估算器补齐未返回的token字段
        TokenUsage completed = control.estimator().complete(raw, messages, tools, response);
        if (canceled)
            control.afterModelCanceled(completed);
        else
            control.afterModel(completed);
    }

    /**
     * 获取全流程累计Token用量
     * @return 汇总TokenUsage
     */
    public TokenUsage total() { return control.usage(); }

}
