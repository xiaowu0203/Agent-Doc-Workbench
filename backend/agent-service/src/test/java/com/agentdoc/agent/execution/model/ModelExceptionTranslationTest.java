package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelErrorType;
import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelExceptionTranslationTest {

    private final ExposedAdapter adapter = new ExposedAdapter();

    @Test
    void translatesAuthenticationFailureAsNonRetryable() {
        ModelProviderException exception = adapter.translate(context(), responseException(401, "Unauthorized"));

        assertEquals(ModelErrorType.AUTHENTICATION, exception.getErrorType());
        assertEquals(401, exception.getStatusCode());
        assertFalse(exception.isRetryable());
    }

    @Test
    void translatesRateLimitAsRetryable() {
        ModelProviderException exception = adapter.translate(context(), responseException(429, "Too Many Requests"));

        assertEquals(ModelErrorType.RATE_LIMIT, exception.getErrorType());
        assertTrue(exception.isRetryable());
    }

    @Test
    void translatesContextLengthAsNonRetryable() {
        ModelProviderException exception = adapter.translate(context(),
                responseException(400, "maximum context length exceeded"));

        assertEquals(ModelErrorType.CONTEXT_LENGTH, exception.getErrorType());
        assertFalse(exception.isRetryable());
    }

    @Test
    void translatesSpringAiTransientFailureAsRetryable() {
        ModelProviderException exception = adapter.translate(context(), new TransientAiException("upstream unavailable"));

        assertEquals(ModelErrorType.PROVIDER_UNAVAILABLE, exception.getErrorType());
        assertTrue(exception.isRetryable());
    }

    private ModelAdapterContext context() {
        ModelEntity model = new ModelEntity();
        model.setProvider("openai");
        return new ModelAdapterContext(null, model, "key", null, java.util.List.of());
    }

    private WebClientResponseException responseException(int status, String message) {
        return new WebClientResponseException(status, message, HttpHeaders.EMPTY,
                message.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private static final class ExposedAdapter extends AbstractSpringAiModelAdapter {

        private ExposedAdapter() {
            super(new ModelChatModelCache(2));
        }

        @Override
        public Set<ModelAdapterType> supportedTypes() {
            return Set.of(ModelAdapterType.OPENAI_CHAT);
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(true, false);
        }

        private ModelProviderException translate(ModelAdapterContext context, RuntimeException exception) {
            return translateException(context, exception);
        }

        @Override
        protected ChatModel chatModel(ModelAdapterContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
