package com.agentdoc.agent.execution.model;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Google Gemini(GenAI) 模型适配器实现
 * <p>
 * 继承 {@link AbstractSpringAiModelAdapter}，复用父类通用能力：ChatModel实例缓存、同步/流式调用、分片合并、
 * 统一异常翻译、连通性测试、通用token解析逻辑。
 * </p>
 * <p>
 * 职责：
 * <ul>
 *     <li>构建Google GenAI SDK的{@link Client}客户端与{@link GoogleGenAiChatModel}实例；</li>
 *     <li>包装{@link ClientBackedChatModel}，实现{@link AutoCloseable}，使缓存淘汰时可以关闭底层google sdk Client资源；</li>
 *     <li>组装单次请求动态参数：任务级工具回调、maxOutputTokens最大输出限制；</li>
 *     <li>解析Gemini特有cached‑content缓存token用量。</li>
 * </ul>
 * <p>
 * 重要约束：
 * <ul>
 *     <li>{@link #chatModel(ModelAdapterContext)} 的defaultOptions仅存放模型静态配置，<b>禁止放入任务级toolCallbacks</b></li>
 *     <li>任务级动态参数、toolCallbacks全部在 {@link #requestOptions(ModelAdapterContext)} 返回，作用于单次Prompt，不污染缓存ChatModel实例</li>
 *     <li>Google GenAI的{@link Client}持有网络资源，必须通过{@link ClientBackedChatModel}包装实现AutoCloseable，交由{@link ModelChatModelCache}在缓存过期淘汰时执行close释放；创建异常时主动关闭client避免泄漏。</li>
 * </ul>
 */
@Component
public class GoogleGenAiModelAdapter extends AbstractSpringAiModelAdapter {

    /**
     * 构造器传入缓存管理器，交由父类完成ChatModel实例缓存管理
     * @param chatModelCache ChatModel缓存，key为 modelId + configVersion
     */
    public GoogleGenAiModelAdapter(ModelChatModelCache chatModelCache) {
        super(chatModelCache);
    }

    /**
     * 返回当前适配器支持的模型协议类型
     * @return 仅支持 GOOGLE_GENAI(Gemini) 协议
     */
    @Override
    public Set<ModelAdapterType> supportedTypes() {
        return Set.of(ModelAdapterType.GOOGLE_GENAI);
    }

    /**
     * 返回该适配器具备的模型能力
     * <p>参数说明：(支持工具调用,支持流式输出)</p>
     * @return Gemini系列同时支持工具调用与流式输出
     */
    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    /**
     * 【模板抽象方法实现】构建可被缓存的GoogleGenAiChatModel实例
     * <p>
     * ⚠️注意：
     * <ol>
     * <li>此处defaultOptions只放置全局静态属性：模型名称、关闭框架内部工具执行；
     * <b>任务级toolCallbacks、单次请求maxOutputTokens不要放在这里，属于动态请求参数，由{@link #requestOptions}提供</b>，
     * 防止不同任务的工具集合互相泄漏污染缓存实例。</li>
     * <li>Google GenAI {@link Client}持有网络资源；使用{@link ClientBackedChatModel}包装，实现AutoCloseable，缓存淘汰时可以关闭底层Client。</li>
     * <li>构建过程抛出异常，必须主动关闭已经创建成功的Client，避免资源泄漏，并把close异常作为suppressed附加到原始异常上。</li>
     * </ol>
     * @param context 模型适配器上下文，携带解密后apiKey、model实体、配置版本、自定义baseUrl
     * @return 被ClientBackedChatModel包装后的ChatModel实例，会被{@link ModelChatModelCache}缓存复用
     */
    @Override
    protected ChatModel chatModel(ModelAdapterContext context) {
        // 构建链接对象
        Client.Builder clientBuilder = Client.builder()
                // ApiKey
                .apiKey(context.apiKey());
        // 支持私有化/镜像部署自定义baseUrl
        if (context.model().getBaseUrl() != null && !context.model().getBaseUrl().isBlank()) {
            clientBuilder.httpOptions(HttpOptions.builder().baseUrl(context.model().getBaseUrl()).build());
        }
        Client client = clientBuilder.build();
        try {
            // 缓存实例的defaultOptions：仅设置模型名称、关闭Spring‑AI内部自动工具执行；不含任务级动态参数
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    // 模型名称
                    .model(context.model().getModelKey())
                    // 关闭Spring‑AI内部自动工具执行
                    .internalToolExecutionEnabled(false)
                    .build();
            ChatModel chatModel = GoogleGenAiChatModel.builder()
                    // 连接对象
                    .genAiClient(client)
                    // 模型配置
                    .defaultOptions(options)
                    .build();

            // 包装：绑定chatModel与底层Client，实现AutoCloseable用于缓存释放资源
            return new ClientBackedChatModel(chatModel, client);
        } catch (RuntimeException exception) {
            // 构建ChatModel失败，需要手动释放已创建的google client，防止资源泄漏
            try {
                client.close();
            } catch (RuntimeException closeException) {
                // 将关闭时发生的异常附加为被抑制异常，不掩盖原始失败原因
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    /**
     * 返回单次请求动态ChatOptions，组装进Prompt，不会修改缓存ChatModel的defaultOptions
     * <p>
     * 存放任务维度动态参数：当前任务工具回调集合、单轮最大输出token限制。
     * 父类callChatModel / streamChatModel 会把该options绑定到每一次Prompt上。
     * </p>
     * @param context 适配器上下文，包含当前任务toolCallbacks、maxOutputTokens上限
     * @return GoogleGenAiChatOptions 单次请求动态参数
     */
    @Override
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder()
                // 模型
                .model(context.model().getModelKey())
                // 任务级工具回调：每次请求动态携带，不写入缓存ChatModel
                .toolCallbacks(context.toolCallbacks())
                // 关闭Spring‑AI内部自动工具执行
                .internalToolExecutionEnabled(false);
        // 设置单轮最大输出token（模型层面限制）
        if (context.maxOutputTokens() != null) {
            options.maxOutputTokens(context.maxOutputTokens());
        }
        return options.build();
    }

    /**
     * 解析Google Gemini专属cached‑content缓存token
     * <p>
     * Gemini支持上下文缓存能力；getCachedContentTokenCount代表从缓存命中而节省的输入token数量。
     * 识别子类{@link GoogleGenAiUsage}读取扩展字段；不存在则返回{@link TokenValue#unavailable()}。
     * </p>
     * @param usage SpringAI标准usage元数据对象，强转为GoogleGenAiUsage读取厂商扩展字段
     * @return 存在缓存token返回provider类型TokenValue，否则返回unavailable
     */
    @Override
    protected TokenValue cachedInputTokens(Usage usage) {
        if (usage instanceof GoogleGenAiUsage googleUsage
                && googleUsage.getCachedContentTokenCount() != null) {
            return TokenValue.provider(googleUsage.getCachedContentTokenCount().longValue());
        }
        return TokenValue.unavailable();
    }

    /**
     * 包装类：将 Gemini ChatModel 与其专属 {@link Client} 绑定，实现 {@link AutoCloseable}
     * <p>
     * 背景：Google GenAI SDK的{@link Client}持有http连接资源，必须手动close；
     * 本类使用委托模式，全部ChatModel接口代理给原始chatModel；额外实现close方法关闭底层Client。
     * 交给{@link ModelChatModelCache}缓存，当缓存条目淘汰、销毁时会调用close释放google sdk客户端资源。
     * </p>
     */
    private static final class ClientBackedChatModel implements ChatModel, AutoCloseable {
        /** 被代理的原始GoogleGenAiChatModel */
        private final ChatModel delegate;
        /** Google GenAI底层SDK客户端，持有网络IO资源，需要手动关闭 */
        private final Client client;

        private ClientBackedChatModel(ChatModel delegate, Client client) {
            this.delegate = delegate;
            this.client = client;
        }

        /**
         * 代理同步聊天调用，直接委托给内部chatModel实现
         * @param prompt 请求prompt对象
         * @return 模型聊天响应
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            return delegate.call(prompt);
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return delegate.getDefaultOptions();
        }

        /**
         * 代理流式聊天调用，直接委托给内部chatModel实现
         * @param prompt 请求prompt对象
         * @return 流式响应Flux
         */
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return delegate.stream(prompt);
        }

        /**
         * {@link AutoCloseable}实现：释放Google GenAI底层Client网络资源
         * <p>由{@link ModelChatModelCache}在缓存对象淘汰/销毁时调用。</p>
         */
        @Override
        public void close() {
            client.close();
        }
    }
}
