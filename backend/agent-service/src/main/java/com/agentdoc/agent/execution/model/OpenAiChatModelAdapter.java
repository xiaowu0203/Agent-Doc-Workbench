package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * OpenAI / OpenAI‑Compatible 模型适配器实现
 * <p>
 * 继承 {@link AbstractSpringAiModelAdapter}，复用父类通用能力：缓存、同步/流式调用、异常翻译、连通性测试、Token解析。
 * 职责：根据模型上下文构建 OpenAI Spring‑AI {@link OpenAiChatModel} 实例；
 * 处理单次请求动态参数（工具回调、maxCompletionTokens）；解析 OpenAI 特有 cached prompt tokens。
 * </p>
 * <p>
 * 重要约束：
 * <ul>
 *     <li>{@link #chatModel(ModelAdapterContext)} 构建带defaultOptions的可缓存ChatModel；<b>defaultOptions禁止携带任务级toolCallbacks</b></li>
 *     <li>任务级动态参数、toolCallbacks全部在 {@link #requestOptions(ModelAdapterContext)} 返回，作用于单次Prompt，不污染缓存实例</li>
 *     <li>支持标准OpenAI以及兼容OpenAI协议的第三方模型服务</li>
 * </ul>
 *
 * supportedTypes：
 * <li>{@link ModelAdapterType#OPENAI_CHAT}：标准OpenAI服务</li>
 * <li>{@link ModelAdapterType#OPENAI_COMPATIBLE}：OpenAI协议兼容服务（通义、DeepSeek等）</li>
 */
@Component
public class OpenAiChatModelAdapter extends AbstractSpringAiModelAdapter {

    /**
     * 构造器注入ChatModel缓存管理器，交给父类维护实例缓存
     * @param chatModelCache ChatModel实例缓存，以modelId+configVersion作为缓存key
     */
    public OpenAiChatModelAdapter(ModelChatModelCache chatModelCache) {
        super(chatModelCache);
    }

    /**
     * 当前适配器支持的模型协议类型
     * @return 支持 OpenAI原生 与 OpenAI‑Compatible兼容协议
     */
    @Override
    public Set<ModelAdapterType> supportedTypes() {
        return Set.of(ModelAdapterType.OPENAI_CHAT, ModelAdapterType.OPENAI_COMPATIBLE);
    }

    /**
     * 返回该适配器具备的模型能力
     * <p>参数说明：(支持工具调用,支持流式输出)</p>
     * @return 能力描述对象，OpenAI系列均支持工具调用、流式
     */
    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    /**
     * 【模板抽象方法实现】构建OpenAiChatModel实例，该实例会被{@link ModelChatModelCache}缓存复用
     * <p>
     * ⚠️注意：此处的defaultOptions只放模型静态属性（model名称、关闭框架内部工具执行）；
     * <b>绝对不要放入任务级toolCallbacks、单次请求maxOutputTokens，这些属于请求动态参数，由{@link #requestOptions}提供</b>，
     * 避免不同任务之间工具集合互相泄漏污染缓存实例。
     * </p>
     * @param context 模型适配器上下文，携带解密后的apiKey、model实体信息
     * @return OpenAiChatModel 实例，会被缓存管理器缓存
     */
    @Override
    protected ChatModel chatModel(ModelAdapterContext context) {
        // 获取模型信息
        ModelEntity model = context.model();
        // 构建OpenAI底层API客户端，baseUrl支持兼容模式自定义endpoint
        OpenAiApi api = OpenAiApi.builder()
                // 解析并设置请求Url
                .baseUrl(ModelEndpoint.resolve(model))
                // ApiKey
                .apiKey(context.apiKey())
                .build();
        // 缓存实例的defaultOptions：仅设置模型名称、关闭Spring‑AI内部自动工具执行；不含任务级动态参数
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                // 模型名称
                .model(model.getModelKey())
                // 关闭Spring‑AI内部自动工具执行
                .internalToolExecutionEnabled(false)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 返回单次请求动态ChatOptions；Prompt级别生效，不修改缓存ChatModel的defaultOptions
     * <p>
     * 把任务维度的toolCallbacks、maxCompletionTokens放在这里。父类会将此options组装进每一次的{@link org.springframework.ai.chat.prompt.Prompt}。
     * </p>
     * @param context 适配器上下文，包含当前任务的工具回调集合、单轮最大输出token限制
     * @return OpenAiChatOptions 单次请求动态参数
     */
    @Override
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                // 模型
                .model(context.model().getModelKey())
                // 任务级工具回调：每次请求动态携带，不写入缓存ChatModel
                .toolCallbacks(context.toolCallbacks())
                // 关闭Spring‑AI内部自动工具执行
                .internalToolExecutionEnabled(false);
        // 设置单轮最大输出token（模型层面限制）
        if (context.maxOutputTokens() != null) {
            options.maxCompletionTokens(context.maxOutputTokens());
        }
        // 温度设置
        if (context.temperature() != null) {
            options.temperature(context.temperature());
        }
        return options.build();
    }

    /**
     * 解析OpenAI专属cached prompt tokens（缓存输入token，OpenAI部分模型支持）
     * <p>cachedTokens代表命中上下文缓存节省的输入token数量；取nativeUsage底层扩展字段。
     * 其他厂商不支持该字段直接返回 {@link TokenValue#unavailable()}（父类默认行为）。
     * </p>
     * @param usage SpringAI标准usage元数据对象，内部包装OpenAI原生Usage对象
     * @return 若存在cachedTokens返回provider类型TokenValue；否则返回unavailable
     */
    @Override
    protected TokenValue cachedInputTokens(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof OpenAiApi.Usage nativeUsage)
                || nativeUsage.promptTokensDetails() == null
                || nativeUsage.promptTokensDetails().cachedTokens() == null) {
            return TokenValue.unavailable();
        }
        return TokenValue.provider(nativeUsage.promptTokensDetails().cachedTokens().longValue());
    }
}
