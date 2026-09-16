package com.agentdoc.common.logging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void classifiesSensitiveKeysWithoutTokenPluralFalsePositive() {
        SensitiveFieldPolicy policy = SensitiveFieldPolicy.defaults();

        assertThat(policy.isSensitive("headers.Authorization", "test-value")).isTrue();
        assertThat(policy.isSensitive("access_token", "test-value")).isTrue();
        assertThat(policy.isSensitive("input_tokens", 12L)).isFalse();
        assertThat(policy.isSensitive("unknown_tokens", 12L)).isTrue();
        assertThat(policy.isSensitive("secret_count", 2L)).isFalse();
        assertThat(policy.isSensitive("secret_count", "2")).isTrue();
    }

    @Test
    void dynamicPoliciesAreImmutableAndDoNotLeakAcrossSessions() {
        SensitiveFieldPolicy base = SensitiveFieldPolicy.defaults();
        SensitiveFieldPolicy first = base.withSensitiveKeys(java.util.List.of("custom-key"));
        SensitiveFieldPolicy second = base.withSensitiveKeys(java.util.List.of("other-key"));

        assertThat(first.isSensitive("custom_key", "value")).isTrue();
        assertThat(first.isSensitive("other_key", "value")).isFalse();
        assertThat(second.isSensitive("custom_key", "value")).isFalse();
    }

    @Test
    void sanitizesNestedMapAndContentFields() {
        Map<String, Object> sanitized = LogSanitizer.sanitizeMap(Map.of(
                "input_tokens", 12L,
                "headers", Map.of("x-api-key", "test-value"),
                "payload", "document text"), SensitiveFieldPolicy.defaults());

        assertThat(sanitized.get("input_tokens")).isEqualTo(12L);
        assertThat(sanitized.get("payload")).isEqualTo(LogSanitizer.REDACTED);
        assertThat(((Map<?, ?>) sanitized.get("headers")).get("x-api-key"))
                .isEqualTo(LogSanitizer.REDACTED);
    }

    @Test
    void removesUrlSecretsAndCredentialValuePatterns() {
        String text = LogSanitizer.sanitizeText(
                "endpoint=https://user:pass@example.test/mcp?access_token=test-value#fragment "
                        + "Authorization: Bearer test-value");

        assertThat(text).contains("https://example.test/mcp");
        assertThat(text).doesNotContain("user:pass", "access_token", "test-value", "fragment");
        assertThat(LogSanitizer.sanitizeUrl("not a valid url")).isEqualTo(LogSanitizer.REDACTED);
    }

    @Test
    void sanitizesJsonAndThrowableWithoutChangingSource() {
        String json = "{\"customCredential\":\"test-value\",\"status\":\"ok\"}";
        SensitiveFieldPolicy policy = SensitiveFieldPolicy.defaults()
                .withSensitiveKeys(java.util.List.of("customCredential"));
        IllegalStateException failure = new IllegalStateException("Bearer test-value");

        assertThat(LogSanitizer.sanitizeJson(json, policy))
                .isEqualTo("{\"customCredential\":\"[REDACTED]\",\"status\":\"ok\"}");
        assertThat(LogSanitizer.sanitizeThrowable(failure))
                .contains(IllegalStateException.class.getName()).doesNotContain("test-value");
        assertThat(failure.getMessage()).isEqualTo("Bearer test-value");
    }
}
