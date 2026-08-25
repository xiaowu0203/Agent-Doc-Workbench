package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.function.Consumer;

import java.util.Set;

/**
 * 模型调用适配器。业务 Runtime 只依赖这个边界，不感知具体供应商 SDK。
 */
public interface ModelAdapter {

    Set<ModelAdapterType> supportedTypes();

    ModelCapabilities capabilities();

    ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages);

    /**
     * 发起单轮流式模型调用，并将文本增量交给上层；工具调用仍由上层在本轮结束后统一处理。
     * 默认回退到非流式调用，保证第三方适配器可以渐进接入。
     */
    default ModelTurnResult stream(ModelAdapterContext context, List<Message> messages,
                                   Consumer<String> onTextDelta) {
        return callOnce(context, messages);
    }

    /**
     * 测试模型配置连通性。默认实现只发起一次最小模型请求，不进入工具循环。
     */
    default void testConnect(ModelAdapterContext context) {
        callOnce(context, List.of(new UserMessage("ping")));
    }
}
