package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Anthropic Claude 模型适配器实现
 * <p>
 * 继承 {@link AbstractSpringAiModelAdapter}，复用父类通用能力：ChatModel实例缓存、同步/流式调用、分片合并、
 * 统一异常翻译、连通性测试、通用token解析逻辑。
 * </p>
 * <p>
 * 职责：
 * <ul>
 *     <li>根据数据库模型配置构建 Anthropic Spring‑AI {@link AnthropicChatModel} 实例，交由缓存管理器复用</li>
 *     <li>组装单次请求动态参数：任务级工具回调、maxTokens最大输出限制</li>
 *     <li>解析Anthropic特有缓存读取token（cache_read_input_tokens）</li>
 * </ul>
 * <p>
 * 重要约束：
 * <ul>
 *     <li>{@link #chatModel(ModelAdapterContext)} 的defaultOptions仅存放模型静态配置，<b>禁止放入任务级toolCallbacks</b></li>
 *     <li>任务级动态参数、toolCallbacks全部在 {@link #requestOptions(ModelAdapterContext)} 返回，作用于单次Prompt，不污染缓存ChatModel实例</li>
 * </ul>
 */
@Component
public class AnthropicModelAdapter extends AbstractSpringAiModelAdapter {

    /**
     * 构造器传入缓存管理器，交由父类完成ChatModel实例缓存管理
     * @param chatModelCache ChatModel缓存，key为 modelId + configVersion
     */
    public AnthropicModelAdapter(ModelChatModelCache chatModelCache) {
        super(chatModelCache);
    }

    /**
     * 返回当前适配器支持的模型协议类型
     * @return 仅支持 ANTHROPIC_MESSAGES 协议
     */
    @Override
    public Set<ModelAdapterType> supportedTypes() {
        return Set.of(ModelAdapterType.ANTHROPIC_MESSAGES);
    }

    /**
     * 返回该适配器具备的模型能力
     * <p>参数说明：(支持工具调用,支持流式输出)</p>
     * @return Anthropic Claude系列同时支持工具调用与流式输出
     */
    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    /**
     * 【模板抽象方法实现】构建可被缓存的AnthropicChatModel实例
     * <p>
     * ⚠️注意：此处defaultOptions只放置全局静态属性：模型名称、关闭框架内部工具执行；
     * <b>任务级toolCallbacks、单次请求maxTokens不要放在这里，属于动态请求参数，由{@link #requestOptions}提供</b>，
     * 防止不同任务的工具集合互相泄漏污染缓存实例。
     * </p>
     * @param context 模型适配器上下文，携带解密后apiKey、model实体、配置版本
     * @return AnthropicChatModel实例，会被{@link ModelChatModelCache}缓存复用
     */
    @Override
    protected ChatModel chatModel(ModelAdapterContext context) {
        // 获取模型信息
        ModelEntity model = context.model();
        // 构建Anthropic底层API客户端，支持自定义baseUrl私有化部署场景
        AnthropicApi api = AnthropicApi.builder()
                // 解析并设置请求Url
                .baseUrl(ModelEndpoint.resolve(model))
                // ApiKey
                .apiKey(context.apiKey())
                .build();
        // 缓存实例默认选项：仅静态模型配置，无任务相关动态数据
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                // 模型名称
                .model(model.getModelKey())
                // 关闭Spring‑AI内部自动工具执行
                .internalToolExecutionEnabled(false)
                .build();
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 返回单次请求动态ChatOptions，组装进Prompt，不会修改缓存ChatModel的defaultOptions
     * <p>
     * 存放任务维度动态参数：当前任务工具回调集合、单轮最大输出token限制。
     * 父类callChatModel / streamChatModel 会把该options绑定到每一次Prompt上。
     * </p>
     * @param context 适配器上下文，包含当前任务toolCallbacks、maxOutputTokens上限
     * @return AnthropicChatOptions 单次请求动态参数
     */
    @Override
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                // 模型
                .model(context.model().getModelKey())
                // 任务级工具回调：每次请求动态携带，不写入缓存ChatModel
                .toolCallbacks(context.toolCallbacks())
                // 关闭Spring‑AI内部自动工具执行
                .internalToolExecutionEnabled(false);
        // 设置单轮最大输出token（模型层面限制）
        if (context.maxOutputTokens() != null) {
            options.maxTokens(context.maxOutputTokens());
        }
        return options.build();
    }

    /**
     * 解析Anthropic专属缓存读取token cache_read_input_tokens
     * <p>
     * Anthropic支持prompt缓存能力；cacheReadInputTokens代表从缓存命中而节省的输入token数量。
     * 从usage的nativeUsage底层扩展对象读取该字段；不存在则返回{@link TokenValue#unavailable()}。
     * </p>
     * @param usage SpringAI标准usage元数据对象，内部封装Anthropic原生Usage对象
     * @return 存在缓存token返回provider类型TokenValue，否则返回unavailable
     */
    @Override
    protected TokenValue cachedInputTokens(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof AnthropicApi.Usage nativeUsage)) {
            return TokenValue.unavailable();
        }
        return TokenValue.provider(nativeUsage.cacheReadInputTokens() == null
                ? null : nativeUsage.cacheReadInputTokens().longValue());
    }
}
