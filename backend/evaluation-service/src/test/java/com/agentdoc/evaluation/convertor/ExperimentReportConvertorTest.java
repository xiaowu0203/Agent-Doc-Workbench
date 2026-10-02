package com.agentdoc.evaluation.convertor;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import static com.agentdoc.evaluation.enums.ExperimentReportCompatibility.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExperimentReportConvertorTest {
    private final RedisUtils redis = mock(RedisUtils.class);
    private final ExperimentReportConvertor convertor = new ExperimentReportConvertor(redis);

    @Test
    void v1KeepsFieldShapeNullFalseZeroAndEmptyStringWithoutChangingRawHash() throws Exception {
        ExperimentReportEntity entity = fixture();
        String original = entity.getReportJson();
        var result = convertor.toVO(entity);
        assertThat(result.compatible()).isTrue();
        assertThat(result.compatibilityCode()).isEqualTo(SUPPORTED);
        assertThat(JsonUtils.parse(JsonUtils.toJson(result.report()), JsonNode.class))
                .isEqualTo(JsonUtils.parse(original, JsonNode.class));
        assertThat(result.report().cells().getFirst().value().booleanValue()).isFalse();
        assertThat(result.report().cells().getFirst().value().stringValue()).isEmpty();
        assertThat(result.report().authorizedTokenBudget()).isZero();
        assertThat(result.report().actualTokenUsage()).isNull();
        assertThat(entity.getReportJson()).isEqualTo(original);
        assertThat(result.contentHash()).isEqualTo(StableSnapshotUtils.sha256Utf8(original));
        verifyNoInteractions(redis);
    }

    @Test
    void schemaDecisionsPrecedePayloadOrHashWithoutGuessingV1() throws Exception {
        ExperimentReportEntity entity = fixture();
        entity.setReportJson("invalid");
        entity.setReportSchemaVersion(null);
        assertThat(convertor.toVO(entity).compatibilityCode()).isEqualTo(SCHEMA_MISSING);
        entity.setReportSchemaVersion(0);
        assertThat(convertor.toVO(entity).compatibilityCode()).isEqualTo(SCHEMA_INVALID);
        entity.setReportSchemaVersion(2);
        var unsupported = convertor.toVO(entity);
        assertThat(unsupported.compatibilityCode()).isEqualTo(SCHEMA_UNSUPPORTED);
        assertThat(unsupported.report()).isNull();
        assertThat(unsupported.selectedRecordIds()).isNull();
        verifyNoInteractions(redis);
    }

    @Test
    void rejectsUnknownMissingWrongAndFractionalIdentityFieldsWithoutCoercion() throws Exception {
        for (String mutation : new String[]{"unknown", "missing", "coerce", "fractional", "overflow", "null-list"}) {
            ExperimentReportEntity entity = fixture();
            ObjectNode tree = (ObjectNode) JsonUtils.parse(entity.getReportJson(), JsonNode.class);
            switch (mutation) {
                case "unknown" -> tree.put("prompt", "must-not-be-returned");
                case "missing" -> tree.remove("cells");
                case "coerce" -> tree.put("variantCount", "2");
                case "fractional" -> ((ObjectNode) tree.path("cases").get(0)).put("taskId", 1.5);
                case "overflow" -> tree.put("expectedCaseCount", Long.MAX_VALUE);
                default -> tree.putNull("feedback");
            }
            entity.setReportJson(JsonUtils.toJson(tree));
            entity.setContentHash(StableSnapshotUtils.sha256Utf8(entity.getReportJson()));
            var result = convertor.toVO(entity);
            assertThat(result.compatibilityCode()).as(mutation).isEqualTo(PAYLOAD_INVALID);
            assertThat(result.report()).isNull();
            assertThat(result.selectedRecordIds()).isNull();
        }
    }

    @Test
    void invalidSelectedIdsAndJsonDoNotFallInto500() throws Exception {
        ExperimentReportEntity entity = fixture();
        entity.setSelectedRecordIdsJson("{\"runIds\":[\"1\"]}");
        assertThat(convertor.toVO(entity).compatibilityCode()).isEqualTo(PAYLOAD_INVALID);
        entity = fixture();
        entity.setReportJson(entity.getReportJson() + " {}");
        entity.setContentHash(StableSnapshotUtils.sha256Utf8(entity.getReportJson()));
        assertThat(convertor.toVO(entity).compatibilityCode()).isEqualTo(PAYLOAD_INVALID);
        entity = fixture();
        entity.setReportJson("{");
        assertThat(convertor.toVO(entity).compatibilityCode()).isEqualTo(PAYLOAD_INVALID);
    }

    @Test
    void hashUsesPersistedUtf8TextAndDeduplicatesIntegrityAlert() throws Exception {
        ExperimentReportEntity entity = fixture();
        String originalHash = entity.getContentHash();
        entity.setReportJson(entity.getReportJson() + " ");
        when(redis.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true, false);
        var first = convertor.toVO(entity);
        var second = convertor.toVO(entity);
        assertThat(first.compatibilityCode()).isEqualTo(CONTENT_HASH_MISMATCH);
        assertThat(second.report()).isNull();
        assertThat(entity.getContentHash()).isEqualTo(originalHash);
        assertThat(first.contentHash()).isEqualTo(originalHash);
        verify(redis, times(2)).setIfAbsent("experiment:report:integrity:99", "alerted", Duration.ofHours(1));
    }

    @Test
    void alertBackendUnavailableStillReturnsSafeCompatibilityResponse() throws Exception {
        ExperimentReportEntity entity = fixture();
        entity.setContentHash("invalid");
        when(redis.setIfAbsent(any(), any(), any(Duration.class))).thenThrow(new IllegalStateException("unavailable"));
        assertThat(convertor.toVO(entity).compatibilityCode()).isEqualTo(CONTENT_HASH_MISMATCH);
    }

    private ExperimentReportEntity fixture() throws Exception {
        ExperimentReportEntity entity = new ExperimentReportEntity();
        entity.setId(99L);
        entity.setExperimentId(1L);
        entity.setRevision(1);
        entity.setReportSchemaVersion(1);
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/experiment-report-v1.json"))) {
            entity.setReportJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        entity.setSelectedRecordIdsJson("{\"runIds\":[1],\"attemptIds\":[4],\"resultIds\":[6],\"metricIds\":[7],\"evidenceIds\":[8],\"feedbackIds\":[10]}");
        entity.setContentHash(StableSnapshotUtils.sha256Utf8(entity.getReportJson()));
        return entity;
    }
}
