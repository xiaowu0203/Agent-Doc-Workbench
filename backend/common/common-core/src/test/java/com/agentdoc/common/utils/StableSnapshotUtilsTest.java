package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StableSnapshotUtilsTest {

    @Test
    void hashIsStableAcrossMapInsertionOrderAndKeepsExplicitNull() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", List.of(2, 1));
        first.put("a", null);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", null);
        second.put("z", List.of(2, 1));

        assertThat(StableSnapshotUtils.snapshotHash(1, first))
                .isEqualTo(StableSnapshotUtils.snapshotHash(1, second));
        assertThat(StableSnapshotUtils.toCanonicalJson(first)).isEqualTo("{\"a\":null,\"z\":[2,1]}");
    }

    @Test
    void schemaVersionAndSemanticArrayOrderParticipateInIdentity() {
        Map<String, Object> snapshot = Map.of("items", List.of("a", "b"));

        assertThat(StableSnapshotUtils.snapshotHash(1, snapshot))
                .isNotEqualTo(StableSnapshotUtils.snapshotHash(2, snapshot));
        assertThat(StableSnapshotUtils.snapshotHash(1, snapshot))
                .isNotEqualTo(StableSnapshotUtils.snapshotHash(1, Map.of("items", List.of("b", "a"))));
    }

    @Test
    void canonicalSnapshotMatchesGoldenUtf8Hash() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("z", List.of(2, 1));
        snapshot.put("a", null);

        assertThat(StableSnapshotUtils.snapshotHash(1, snapshot))
                .isEqualTo("7523c04e8e5452cf26d285fa1c787da1042476ea62ec108023339c2a36f877dc");
    }
}
