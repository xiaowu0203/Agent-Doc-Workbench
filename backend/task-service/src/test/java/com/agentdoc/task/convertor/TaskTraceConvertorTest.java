package com.agentdoc.task.convertor;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.constant.TaskTraceConstant;
import com.agentdoc.task.enums.TaskTraceAvailability;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTraceConvertorTest {
    private static final String TRACE = "a".repeat(32);
    private final TaskTraceConvertor convertor = new TaskTraceConvertor();

    @Test
    void keepsOwnedBranchAndSharedAncestorsButNotOtherRunBranchesOrSensitiveFields() {
        List<Map<String, Object>> spans = List.of(
                span(1, null, "GET /secret?token=abc", List.of(tag("http.request.method", "GET"), tag("execution.id", 999))),
                span(2, 1, "agentdoc.agent.execute", List.of(tag("run.id", 11), tag("agentdoc.space.id", 7))),
                span(3, 2, "gen_ai.client.operation", List.of(tag("execution.id", 22),
                        tag("gen_ai.request.model", "test-model"), tag("gen_ai.usage.input_tokens", 0),
                        tag("gen_ai.prompt", "forbidden-prompt"), tag("http.url", "secret-url"),
                        tag("agentdoc.operation.status", "failed"), tag("agentdoc.retry.count", 2))),
                span(4, 1, "agentdoc.agent.execute", List.of(tag("run.id", 12))),
                span(5, 4, "secret-foreign-span", List.of(tag("gen_ai.request.model", "foreign-model"))));
        var view = convertor.project(11L, 7L, TRACE, payload(spans));
        assertThat(view.availabilityCode()).isEqualTo(TaskTraceAvailability.AVAILABLE);
        assertThat(view.spans()).extracting(item -> item.spanId()).containsExactly(id(1), id(2), id(3));
        assertThat(view.spanCount()).isEqualTo(3);
        assertThat(view.errorCount()).isEqualTo(1);
        assertThat(view.retryCount()).isEqualTo(2);
        assertThat(view.spans().get(0).name()).isEqualTo("http.request");
        assertThat(view.spans().get(0).attributes()).isEmpty();
        assertThat(view.spans().get(2).parentSpanId()).isEqualTo(id(2));
        String json = JsonUtils.toJson(view);
        assertThat(json).doesNotContain("forbidden-prompt", "secret-url", "foreign-model", "secret-foreign-span", "token=abc");
        assertThat(view.spans().get(2).attributes()).anySatisfy(attribute -> {
            assertThat(attribute.key()).isEqualTo("gen_ai.usage.input_tokens");
            assertThat(attribute.value()).isEqualTo("0");
        });
    }

    @Test
    void preservesJaegerInt64StringAttributesWithoutCoercingInvalidValues() {
        var view = convertor.project(11L, 7L, TRACE, payload(List.of(
                span(1, null, "agentdoc.agent.execute", List.of(tag("run.id", "11"),
                        tag("execution.id", "2104036078955040770"), tag("agentdoc.retry.count", "2"),
                        tag("gen_ai.usage.input_tokens", "0"), tag("agentdoc.skill.id", "01"),
                        tag("agentdoc.skill.version_id", "9223372036854775808"))))));
        assertThat(view.availabilityCode()).isEqualTo(TaskTraceAvailability.AVAILABLE);
        assertThat(view.retryCount()).isEqualTo(2);
        assertThat(view.spans().getFirst().attributes()).anySatisfy(attribute -> {
            assertThat(attribute.key()).isEqualTo("execution.id");
            assertThat(attribute.valueType()).isEqualTo("LONG");
            assertThat(attribute.value()).isEqualTo("2104036078955040770");
        });
        assertThat(view.spans().getFirst().attributes()).anySatisfy(attribute -> {
            assertThat(attribute.key()).isEqualTo("gen_ai.usage.input_tokens");
            assertThat(attribute.value()).isEqualTo("0");
        });
        assertThat(view.spans().getFirst().attributes()).extracting(attribute -> attribute.key())
                .doesNotContain("agentdoc.skill.id", "agentdoc.skill.version_id");
    }

    @Test
    void rejectsUnrelatedAndWrongSpaceEvenWhenTraceIdMatches() {
        assertThat(convertor.project(11L, 7L, TRACE, payload(List.of(
                span(1, null, "agentdoc.agent.execute", List.of(tag("run.id", 12)))))).availabilityCode())
                .isEqualTo(TaskTraceAvailability.UNRELATED_TRACE);
        assertThat(convertor.project(11L, 7L, TRACE, payload(List.of(
                span(1, null, "agentdoc.agent.execute", List.of(tag("run.id", 11), tag("agentdoc.space.id", 8)))))).availabilityCode())
                .isEqualTo(TaskTraceAvailability.UNRELATED_TRACE);
    }

    @Test
    void mapsDynamicDatabaseNamesAndPreservesCancellationAndBooleanAttributes() {
        var view = convertor.project(11L, 7L, TRACE, payload(List.of(
                span(1, null, "agentdoc.agent.execute", List.of(tag("run.id", 11))),
                span(2, 1, "SELECT private_body FROM document", List.of(tag("db.system.name", "mysql"),
                        tag("db.statement", "forbidden-sql"), tag("agentdoc.operation.status", "canceled"),
                        tag("agentdoc.mcp.external", false))))));
        assertThat(view.spans().get(1).name()).isEqualTo("db.client.operation");
        assertThat(view.spans().get(1).category()).isEqualTo("DATABASE");
        assertThat(view.canceledCount()).isEqualTo(1);
        assertThat(JsonUtils.toJson(view)).doesNotContain("SELECT", "forbidden-sql", "private_body");
    }

    @Test
    void missingParentProducesPartialProjectionWithNoDanglingReference() {
        var view = convertor.project(11L, 7L, TRACE, payload(List.of(
                span(1, 99, "agentdoc.agent.execute", List.of(tag("run.id", 11))))));
        assertThat(view.partial()).isTrue();
        assertThat(view.spans().getFirst().parentSpanId()).isNull();
    }

    @Test
    void invalidPayloadDuplicateIdsCyclesAndOverflowFailClosed() {
        for (JsonNode payload : List.of(
                JsonUtils.parse("{}", JsonNode.class),
                payload(List.of(span(1, null, "x", List.of(tag("run.id", 11))),
                        span(1, null, "x", List.of()))),
                payload(List.of(span(1, 2, "x", List.of(tag("run.id", 11))), span(2, 1, "x", List.of()))))) {
            var view = convertor.project(11L, 7L, TRACE, payload);
            assertThat(view.availabilityCode()).isEqualTo(TaskTraceAvailability.PAYLOAD_INVALID);
            assertThat(view.spanCount()).isNull();
            assertThat(view.spans()).isEmpty();
        }
    }

    @Test
    void truncationKeepsFullCountsAndErrorSummary() {
        List<Map<String, Object>> spans = new ArrayList<>();
        spans.add(span(1, null, "agentdoc.agent.execute", List.of(tag("run.id", 11))));
        for (int i = 2; i <= TaskTraceConstant.MAX_VIEW_SPANS + 1; i++) {
            spans.add(span(i, 1, "technical", List.of(tag("error", true))));
        }
        var view = convertor.project(11L, 7L, TRACE, payload(spans));
        assertThat(view.truncated()).isTrue();
        assertThat(view.spans()).hasSize(TaskTraceConstant.MAX_VIEW_SPANS);
        assertThat(view.spanCount()).isEqualTo(TaskTraceConstant.MAX_VIEW_SPANS + 1);
        assertThat(view.errorCount()).isEqualTo(TaskTraceConstant.MAX_VIEW_SPANS);
    }

    @Test
    void absentTraceAndMalformedTechnicalAttributesDoNotReturnRawValues() {
        var empty = convertor.project(11L, 7L, TRACE, JsonUtils.parse("{\"data\":[]}", JsonNode.class));
        assertThat(empty.availabilityCode()).isEqualTo(TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED);
        assertThat(empty.spanCount()).isNull();
        var view = convertor.project(11L, 7L, TRACE, payload(List.of(span(1, null,
                "https://private.example/token", List.of(tag("run.id", 11),
                        tag("gen_ai.request.model", "Bearer forbidden-token"),
                        tag("agentdoc.tool.source", "arbitrary-free-text"))))));
        assertThat(JsonUtils.toJson(view)).doesNotContain("private.example", "forbidden-token", "arbitrary-free-text");
    }

    private static String id(int value) { return String.format("%016x", value); }
    private static Map<String, Object> tag(String key, Object value) { return Map.of("key", key, "value", value); }
    private static Map<String, Object> span(int id, Integer parent, String name, List<Map<String, Object>> tags) {
        return Map.of("traceID", TRACE, "spanID", id(id), "operationName", name, "processID", "p1",
                "startTime", 1000000L + id, "duration", 100L, "tags", tags,
                "references", parent == null ? List.of() : List.of(
                        Map.of("refType", "CHILD_OF", "traceID", TRACE, "spanID", id(parent))),
                "logs", List.of(Map.of("fields", List.of(tag("prompt", "forbidden-log")))));
    }
    private static JsonNode payload(List<Map<String, Object>> spans) {
        return JsonUtils.parse(JsonUtils.toJson(Map.of("data", List.of(Map.of("traceID", TRACE, "spans", spans,
                "processes", Map.of("p1", Map.of("serviceName", "agent-service", "tags", List.of(
                        tag("resource.secret", "forbidden-resource")))))))), JsonNode.class);
    }
}
