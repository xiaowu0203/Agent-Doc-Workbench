package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.metadata.Usage;
import com.agentdoc.common.pojo.TokenValue;

import java.util.List;
import java.util.function.Consumer;

import java.util.Set;

/**
 * 模型调用适配器 SPI 接口
 * <p>
 * 设计目标：业务 Agent Runtime 只依赖本接口边界，不感知具体大模型厂商、底层SDK实现差异。
 * 做一层防腐隔离，屏蔽 OpenAI / Anthropic / Gemini 等不同模型的SDK细节。
 * </p>
 * <p>
 * 实现分类：
 * <ul>
 *     <li>Spring‑AI系适配器：继承 {@code AbstractSpringAiModelAdapter}，复用模板实现；必须实现 {@link #supportedTypes()}、{@link #capabilities()}、抽象构建ChatModel方法</li>
 *     <li>非Spring‑AI第三方适配器：全部default方法可以重写，做到渐进接入，不需要依赖Spring‑AI ChatModel体系</li>
 * </ul>
 * <p>
 * 重要契约：
 * <ol>
 * <li>{@link #cachedChatModel} 为Spring‑AI体系扩展方法；非SpringAI适配器直接抛出默认异常；</li>
 * <li>任务级动态参数（toolCallbacks、单次maxOutputTokens）<b>禁止修改缓存ChatModel默认配置</b>，通过 {@link #chatOptions} 返回，绑定到单次请求Prompt；</li>
 * <li>{@link #stream} 默认降级调用callOnce，方便尚未实现流式的适配器可以先跑通非流式逻辑；</li>
 * <li>{@link #testConnect} 连通性探测，用于校验模型密钥、baseUrl、网络是否可用，仅发送ping消息，不执行工具循环；</li>
 * <li>{@link #translateForRuntime} 将底层SDK/HTTP异常转换为统一的模型异常，上层Runtime统一处理重试、告警；</li>
 * <li>{@link #tokenUsage} 从ChatResponse解析token用量，提供默认基础实现；厂商有特殊扩展usage字段可在子类重写覆盖。</li>
 * </ol>
 */
public interface ModelAdapter {

    /**
     * 获取当前适配器支持的模型协议类型集合
     * @return 支持的 {@link ModelAdapterType}，一个适配器可支持多种协议（例如OpenAI适配器同时支持OPENAI_CHAT、OPENAI_COMPATIBLE）
     */
    Set<ModelAdapterType> supportedTypes();

    /**
     * 获取该适配器对应模型的能力声明
     * @return {@link ModelCapabilities}，包含是否支持工具调用、是否支持流式输出等标志
     */
    ModelCapabilities capabilities();

    /**
     * 同步单轮模型调用，执行一次LLM请求
     * <p>注意：仅执行一轮模型推理，不做Agent多轮工具循环；多轮循环由上层AgentRuntime控制。</p>
     * @param context 适配器上下文：包含模型实体、解密后apiKey、工具回调集合、输出限制等
     * @param messages 完整对话消息列表
     * @return {@link ModelTurnResult} 单轮模型完整结果，包含原始response、回答文本、token用量
     */
    ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages);

    /**
     * 获取可缓存复用的Spring‑AI {@link ChatModel}实例
     * <p>仅Spring‑AI体系适配器实现该方法；非SpringAI适配器直接抛出默认异常。</p>
     * @param context 适配器上下文
     * @return 可缓存ChatModel实例
     * @throws UnsupportedOperationException 当前适配器不基于Spring‑AI ChatModel实现时抛出
     */
    default ChatModel cachedChatModel(ModelAdapterContext context) {
        throw new UnsupportedOperationException("当前模型适配器不支持直接获取 ChatModel");
    }

    /**
     * 获取本次请求动态ChatOptions，作用于单次Prompt，<b>不修改缓存ChatModel实例默认配置</b>
     * <p>存放任务级动态参数：toolCallbacks、单轮maxOutputTokens等；避免跨任务之间工具集合互相污染缓存实例。</p>
     * @param context 适配器上下文
     * @return ChatOptions，可为null
     */
    default ChatOptions chatOptions(ModelAdapterContext context) {
        return null;
    }

    /**
     * 底层SDK/HTTP异常翻译钩子：把厂商原生异常转换为Runtime层识别的统一模型异常
     * <p>默认直接返回原始异常；Spring‑AI适配器在父类重写，输出 {@code ModelProviderException}。</p>
     * @param context 适配器上下文
     * @param exception 底层抛出的原始运行时异常
     * @return 翻译之后的RuntimeException，上层AgentRuntime做错误分类、重试、日志
     */
    default RuntimeException translateForRuntime(ModelAdapterContext context, RuntimeException exception) {
        return exception;
    }

    /**
     * 从模型原始响应解析Token用量，提供基础默认实现
     * <p>默认只解析标准promptTokens / completionTokens；
     * 如果厂商存在特有扩展token字段（例如OpenAI/Anthropic/Gemini缓存输入token），适配器子类重写此方法补充扩展字段。</p>
     * @param response 模型原始ChatResponse
     * @return 解析后的{@link TokenUsage}对象
     */
    default TokenUsage tokenUsage(ChatResponse response) {
        Usage usage = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getUsage();
        Long input = usage == null || usage.getPromptTokens() == null ? null : usage.getPromptTokens().longValue();
        Long output = usage == null || usage.getCompletionTokens() == null
                ? null : usage.getCompletionTokens().longValue();
        return new TokenUsage(TokenValue.provider(input), TokenValue.unavailable(), TokenValue.provider(output));
    }

    /**
     * 发起单轮流式模型调用
     * <p>
     * 回调 {@code onTextDelta} 向外推送文本增量分片；工具调用分片不向外透传，工具调用逻辑由上层Runtime在本轮结束之后统一处理。
     * </p>
     * <p>
     * 默认实现直接回退调用同步{@link #callOnce}，保证尚未实现流式的第三方适配器可以渐进接入，先跑通非流式业务。
     * </p>
     * @param context 适配器上下文
     * @param messages 对话消息列表
     * @param onTextDelta 文本增量回调，接收模型返回的每一段delta文本
     * @return 流全部消费完成之后返回完整的单轮模型结果
     */
    default ModelTurnResult stream(ModelAdapterContext context, List<Message> messages,
                                   Consumer<String> onTextDelta) {
        return callOnce(context, messages);
    }

    /**
     * 模型连通性测试
     * <p>发送简单ping探测消息，校验模型配置（apiKey、baseUrl、网络连通）是否可用；
     * 只做一次简单请求，不携带工具，不进入Agent工具循环。</p>
     * <p>默认实现复用callOnce；适配器可以重写做特殊探测逻辑。</p>
     * @param context 适配器上下文（连通性测试专用上下文）
     */
    default void testConnect(ModelAdapterContext context) {
        callOnce(context, List.of(new UserMessage("ping")));
    }
}
