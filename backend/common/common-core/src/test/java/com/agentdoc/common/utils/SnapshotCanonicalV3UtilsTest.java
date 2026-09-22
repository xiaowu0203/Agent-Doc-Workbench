package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotCanonicalV3UtilsTest {

    @Test
    void shouldUseUtf8UnsignedKeyOrderAndCanonicalDecimals() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("é", new BigDecimal("1.00"));
        snapshot.put("z", BigDecimal.ZERO);

        String json = SnapshotCanonicalV3Utils.canonicalEnvelope(3, snapshot);

        assertThat(json).isEqualTo("{\"schemaVersion\":3,\"snapshot\":{\"z\":\"0\",\"é\":\"1\"}}");
        assertThat(SnapshotCanonicalV3Utils.hashEnvelope(json)).hasSize(64);
    }

    @Test
    void shouldKeepNullEmptyArrayAndEmptyObjectDistinct() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nullValue", null);
        snapshot.put("array", java.util.List.of());
        snapshot.put("object", Map.of());

        assertThat(SnapshotCanonicalV3Utils.canonicalEnvelope(3, snapshot))
                .contains("\"nullValue\":null", "\"array\":[]", "\"object\":{}");
    }
}
