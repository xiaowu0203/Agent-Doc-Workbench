package com.agentdoc.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsStrictTest {
    @Test
    void strictReadRejectsDuplicateKeysTrailingValuesAndBrokenJson() {
        for (String value : new String[]{"{\"schema\":1,\"schema\":2}", "{} {}", "{} trailing", "{", ""}) {
            assertThat(JsonUtils.parseStrict(value, JsonNode.class)).isNull();
        }
    }

    @Test
    void strictReadKeepsFalseZeroEmptyStringAndNullWithoutChangingLegacyRead() {
        JsonNode value = JsonUtils.parseStrict("{\"zero\":0,\"bool\":false,\"text\":\"\",\"none\":null}", JsonNode.class);
        assertThat(value.path("zero").intValue()).isZero();
        assertThat(value.path("bool").booleanValue()).isFalse();
        assertThat(value.path("text").textValue()).isEmpty();
        assertThat(value.path("none").isNull()).isTrue();
        assertThat(JsonUtils.parse("{\"schema\":1,\"schema\":2}", JsonNode.class).path("schema").intValue()).isEqualTo(2);
    }
}
