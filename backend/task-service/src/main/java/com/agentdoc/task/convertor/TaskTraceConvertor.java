package com.agentdoc.task.convertor;

import com.agentdoc.common.logging.LogSanitizer;
import com.agentdoc.task.constant.TaskTraceConstant;
import com.agentdoc.task.enums.TaskTraceAvailability;
import com.agentdoc.task.pojo.vo.TaskTraceViewVO;
import com.agentdoc.task.pojo.vo.TaskTraceViewVO.AttributeVO;
import com.agentdoc.task.pojo.vo.TaskTraceViewVO.SpanNodeVO;
import com.agentdoc.task.pojo.vo.TaskTraceViewVO.ServiceSummaryVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Jaeger 原始结构到 Task 范围的白名单投影。动态名称、日志、资源全量属性均不透传。 */
@Component
public class TaskTraceConvertor {
    private static final Set<String> SERVICES = Set.of("gateway-service", "auth-service", "task-service",
            "agent-service", "document-service", "evaluation-service");
    private static final Map<String, String> DOMAIN_NAMES = Map.of(
            "agentdoc.agent.execute", "AGENT", "gen_ai.client.operation", "GEN_AI",
            "agentdoc.skill.select", "SKILL", "agentdoc.skill.read", "SKILL",
            "agentdoc.mcp.connect", "MCP", "agentdoc.mcp.tool.execute", "TOOL");
    private static final Set<String> LONG_ATTRIBUTES = Set.of("run.id", "execution.id",
            "agentdoc.retry.count", "agentdoc.model.call.sequence", "gen_ai.usage.input_tokens",
            "gen_ai.usage.output_tokens", "agentdoc.skill.id", "agentdoc.skill.version_id",
            "agentdoc.skill.candidate_count", "agentdoc.skill.selected_count", "agentdoc.mcp.server_id",
            "agentdoc.tool.result_size_bytes");
    private static final Map<String, Set<String>> ENUM_ATTRIBUTES = Map.of(
            "agentdoc.execution.mode", Set.of("LIVE", "ISOLATED"),
            "agentdoc.runtime.type", Set.of("SPRING_AI", "SPRING_AI_ALIBABA", "CUSTOM"),
            "agentdoc.tool.source", Set.of("WORKBENCH", "EXTERNAL_MCP", "SKILL"),
            "agentdoc.skill.selection_mode", Set.of("ALL_BOUND", "ROUTER"),
            "agentdoc.operation.status", Set.of("completed", "failed", "canceled", "running"));
    private static final Set<String> TECHNICAL_ATTRIBUTES = Set.of("gen_ai.operation.name",
            "gen_ai.provider.name", "gen_ai.request.model", "gen_ai.response.model",
            "agentdoc.tool.technical_name", "agentdoc.skill.technical_name", "agentdoc.mcp.transport",
            "agentdoc.tool.result_type");
    private static final Set<String> BOOLEAN_ATTRIBUTES = Set.of("agentdoc.model.stream", "agentdoc.mcp.external");

    public TaskTraceViewVO unavailable(Long taskId, Long spaceId, String traceId, TaskTraceAvailability code) {
        return new TaskTraceViewVO(taskId, spaceId, traceId, code, false, false,
                null, null, null, null, null, null, null, List.of(), List.of());
    }

    public TaskTraceViewVO project(Long taskId, Long spaceId, String traceId, JsonNode payload) {
        try {
            return projectChecked(taskId, spaceId, traceId, payload);
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return unavailable(taskId, spaceId, traceId, TaskTraceAvailability.PAYLOAD_INVALID);
        }
    }

    private TaskTraceViewVO projectChecked(Long taskId, Long spaceId, String traceId, JsonNode payload) {
        JsonNode data = payload == null ? null : payload.get("data");
        require(data != null && data.isArray());
        if (data.isEmpty()) {
            return unavailable(taskId, spaceId, traceId, TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED);
        }
        require(data.size() == 1);
        JsonNode trace = data.get(0);
        if (!Objects.equals(traceId, traceId(trace.path("traceID").asText()))) {
            return unavailable(taskId, spaceId, traceId, TaskTraceAvailability.UNRELATED_TRACE);
        }
        JsonNode spans = trace.get("spans");
        require(spans != null && spans.isArray());
        if (spans.size() > TaskTraceConstant.MAX_RAW_SPANS) {
            return unavailable(taskId, spaceId, traceId, TaskTraceAvailability.PAYLOAD_TOO_LARGE);
        }
        Map<String, RawSpan> indexed = new LinkedHashMap<>();
        boolean partial = trace.hasNonNull("warnings") && !trace.path("warnings").isEmpty();
        for (JsonNode span : spans) {
            require(Objects.equals(traceId, traceId(span.path("traceID").asText())));
            String id = spanId(span.path("spanID").asText());
            require(id != null && !indexed.containsKey(id));
            JsonNode start = span.get("startTime");
            JsonNode duration = span.get("duration");
            require(start != null && start.isIntegralNumber() && start.canConvertToLong() && start.longValue() > 0
                    && duration != null && duration.isIntegralNumber() && duration.canConvertToLong()
                    && duration.longValue() >= 0);
            Math.addExact(start.longValue(), duration.longValue());
            Map<String, JsonNode> tags = tags(span.get("tags"));
            String parent = null;
            JsonNode references = span.get("references");
            require(references != null && references.isArray());
            for (JsonNode reference : references) {
                String relation = reference.path("refType").asText();
                if (Set.of("CHILD_OF", "FOLLOWS_FROM").contains(relation)) {
                    String referenceId = spanId(reference.path("spanID").asText());
                    if (!Objects.equals(traceId, traceId(reference.path("traceID").asText())) || referenceId == null) {
                        partial = true;
                        continue;
                    }
                    if (parent == null || "CHILD_OF".equals(relation)) { parent = referenceId; }
                }
            }
            JsonNode process = trace.path("processes").path(span.path("processID").asText());
            String service = process.path("serviceName").asText();
            if (!process.isObject()) { partial = true; }
            if (!SERVICES.contains(service)) { service = "other-service"; }
            if (span.hasNonNull("warnings") && !span.path("warnings").isEmpty()) { partial = true; }
            indexed.put(id, new RawSpan(id, parent, service, span.path("operationName").asText(),
                    start.longValue(), duration.longValue(), tags,
                    identity(tags.get("run.id")), identity(tags.get("agentdoc.space.id"))));
        }
        Map<String, List<RawSpan>> children = new HashMap<>();
        Set<String> checked = new HashSet<>();
        for (RawSpan span : indexed.values()) {
            Set<String> path = new HashSet<>();
            RawSpan current = span;
            while (current != null && !checked.contains(current.id())) {
                require(path.add(current.id()));
                current = current.parent() == null ? null : indexed.get(current.parent());
            }
            checked.addAll(path);
        }
        for (RawSpan span : indexed.values()) {
            if (span.parent() != null) {
                children.computeIfAbsent(span.parent(), ignored -> new ArrayList<>()).add(span);
            }
        }
        List<RawSpan> seeds = indexed.values().stream()
                .filter(span -> Objects.equals(span.run(), taskId) && belongs(span, taskId, spaceId)).toList();
        if (seeds.isEmpty()) {
            return unavailable(taskId, spaceId, traceId, TaskTraceAvailability.UNRELATED_TRACE);
        }
        Set<String> owned = new HashSet<>();
        ArrayDeque<RawSpan> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            RawSpan span = queue.removeFirst();
            if (!belongs(span, taskId, spaceId) || !owned.add(span.id())) { continue; }
            queue.addAll(children.getOrDefault(span.id(), List.of()));
        }
        Set<String> retained = new HashSet<>(owned);
        // 只保留当前分支的必要祖先，不沿共享祖先展开兄弟分支。
        for (RawSpan seed : seeds) {
            Set<String> path = new HashSet<>();
            RawSpan current = seed;
            while (current.parent() != null) {
                if (!path.add(current.id())) { partial = true; break; }
                RawSpan parent = indexed.get(current.parent());
                if (parent == null || !belongs(parent, taskId, spaceId)) { partial = true; break; }
                boolean alreadyRetained = !retained.add(parent.id());
                current = parent;
                if (alreadyRetained && !owned.contains(parent.id())) { break; }
            }
        }
        List<RawSpan> visible = indexed.values().stream().filter(span -> retained.contains(span.id()))
                .sorted(Comparator.comparingLong(RawSpan::start).thenComparing(RawSpan::id)).toList();
        long start = visible.stream().mapToLong(RawSpan::start).min().orElseThrow();
        long end = visible.stream().mapToLong(span -> Math.addExact(span.start(), span.duration())).max().orElseThrow();
        boolean truncated = visible.size() > TaskTraceConstant.MAX_VIEW_SPANS;
        List<RawSpan> displayed = visible.stream().limit(TaskTraceConstant.MAX_VIEW_SPANS).toList();
        Set<String> displayedIds = new HashSet<>(displayed.stream().map(RawSpan::id).toList());
        List<SpanNodeVO> nodes = new ArrayList<>();
        for (RawSpan span : displayed) {
            String parent = span.parent();
            if (parent != null && !displayedIds.contains(parent)) { partial = true; parent = null; }
            String category = category(span);
            String name = DOMAIN_NAMES.containsKey(span.operation()) ? span.operation() : stableName(category);
            nodes.add(new SpanNodeVO(span.id(), parent, name, span.service(), category,
                    kind(span.tags()), status(span.tags()), span.start(), span.duration(),
                    owned.contains(span.id()) ? attributes(span.tags()) : List.of()));
        }
        List<ServiceSummaryVO> services = new ArrayList<>();
        Map<String, List<RawSpan>> byService = new LinkedHashMap<>();
        visible.forEach(span -> byService.computeIfAbsent(span.service(), ignored -> new ArrayList<>()).add(span));
        byService.forEach((service, items) -> services.add(new ServiceSummaryVO(service, items.size(),
                (int) items.stream().filter(item -> "ERROR".equals(status(item.tags()))).count(),
                items.stream().mapToLong(RawSpan::duration).reduce(0, Math::addExact))));
        return new TaskTraceViewVO(taskId, spaceId, traceId, TaskTraceAvailability.AVAILABLE, partial, truncated,
                start, end, end - start, visible.size(),
                (int) visible.stream().filter(span -> "ERROR".equals(status(span.tags()))).count(),
                (int) visible.stream().filter(span -> "CANCELED".equals(status(span.tags()))).count(),
                visible.stream().mapToLong(span -> nonnegative(span.tags().get("agentdoc.retry.count")))
                        .reduce(0, Math::addExact), List.copyOf(services), List.copyOf(nodes));
    }

    private static boolean belongs(RawSpan span, Long taskId, Long spaceId) {
        return (span.run() == null || Objects.equals(span.run(), taskId))
                && (span.space() == null || Objects.equals(span.space(), spaceId));
    }

    private static Map<String, JsonNode> tags(JsonNode tags) {
        if (tags == null) { return Map.of(); }
        require(tags.isArray());
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode tag : tags) {
            String key = tag.path("key").asText();
            require(!key.isEmpty() && tag.has("value") && !result.containsKey(key));
            result.put(key, tag.get("value"));
        }
        return result;
    }

    private static String traceId(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{16}|[0-9a-fA-F]{32}") || value.matches("0+")) { return null; }
        return "0".repeat(32 - value.length()) + value.toLowerCase(Locale.ROOT);
    }

    private static String spanId(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{1,16}") || value.matches("0+")) { return null; }
        return "0".repeat(16 - value.length()) + value.toLowerCase(Locale.ROOT);
    }

    private static Long identity(JsonNode value) {
        if (value == null) { return null; }
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0) { return value.longValue(); }
        if (value.isTextual() && value.textValue().matches("[1-9][0-9]{0,18}")) {
            try { return Long.parseLong(value.textValue()); } catch (NumberFormatException ignored) { return -1L; }
        }
        return -1L;
    }

    private static long nonnegative(JsonNode value) {
        Long parsed = nonnegativeLong(value);
        return parsed == null ? 0 : parsed;
    }

    /** Jaeger 的 int64 属性可能以十进制字符串返回，禁止浮点数和宽松类型转换。 */
    private static Long nonnegativeLong(JsonNode value) {
        if (value == null) { return null; }
        if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0) {
            return value.longValue();
        }
        if (value.isTextual() && value.textValue().matches("0|[1-9][0-9]{0,18}")) {
            try { return Long.parseLong(value.textValue()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static String text(Map<String, JsonNode> tags, String key) {
        JsonNode value = tags.get(key);
        return value != null && value.isTextual() ? value.textValue() : "";
    }

    private static String kind(Map<String, JsonNode> tags) {
        String value = text(tags, "span.kind").toUpperCase(Locale.ROOT);
        return Set.of("SERVER", "CLIENT", "PRODUCER", "CONSUMER", "INTERNAL").contains(value) ? value : "INTERNAL";
    }

    private static String status(Map<String, JsonNode> tags) {
        String operation = text(tags, "agentdoc.operation.status");
        if ("canceled".equals(operation)) { return "CANCELED"; }
        if ("failed".equals(operation) || "ERROR".equals(text(tags, "otel.status_code"))
                || (tags.containsKey("error") && tags.get("error").isBoolean() && tags.get("error").booleanValue())) {
            return "ERROR";
        }
        if ("completed".equals(operation) || "OK".equals(text(tags, "otel.status_code"))) { return "OK"; }
        return "UNKNOWN";
    }

    private static String category(RawSpan span) {
        if (DOMAIN_NAMES.containsKey(span.operation())) { return DOMAIN_NAMES.get(span.operation()); }
        if ("redis".equals(text(span.tags(), "db.system.name"))) { return "REDIS"; }
        if (span.tags().containsKey("db.system.name")) { return "DATABASE"; }
        if (span.tags().containsKey("messaging.system")) { return "MESSAGING"; }
        if (span.tags().containsKey("rpc.system")) { return "A2A"; }
        if ("gateway-service".equals(span.service())) { return "GATEWAY"; }
        if (span.tags().containsKey("http.request.method")) { return "HTTP"; }
        if ("task-service".equals(span.service())) { return "TASK"; }
        return "TECHNICAL";
    }

    private static String stableName(String category) {
        return switch (category) {
            case "DATABASE" -> "db.client.operation";
            case "REDIS" -> "redis.operation";
            case "MESSAGING" -> "messaging.operation";
            case "A2A" -> "a2a.operation";
            case "GATEWAY" -> "gateway.request";
            case "HTTP" -> "http.request";
            case "TASK" -> "task.operation";
            default -> "technical.operation";
        };
    }

    private static List<AttributeVO> attributes(Map<String, JsonNode> tags) {
        List<AttributeVO> result = new ArrayList<>();
        for (var entry : tags.entrySet()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            Long numeric = LONG_ATTRIBUTES.contains(key) ? nonnegativeLong(value) : null;
            if (numeric != null) {
                result.add(new AttributeVO(key, "LONG", Long.toString(numeric)));
            } else if (BOOLEAN_ATTRIBUTES.contains(key) && value.isBoolean()) {
                result.add(new AttributeVO(key, "BOOLEAN", Boolean.toString(value.booleanValue())));
            } else if (ENUM_ATTRIBUTES.containsKey(key) && value.isTextual()
                    && ENUM_ATTRIBUTES.get(key).contains(value.textValue())) {
                result.add(new AttributeVO(key, "STRING", value.textValue()));
            } else if (TECHNICAL_ATTRIBUTES.contains(key) && value.isTextual()
                    && value.textValue().matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
                    && Objects.equals(value.textValue(), LogSanitizer.sanitizeText(value.textValue()))) {
                result.add(new AttributeVO(key, "STRING", value.textValue()));
            }
        }
        result.sort(Comparator.comparing(AttributeVO::key));
        return List.copyOf(result);
    }

    private static void require(boolean condition) {
        if (!condition) { throw new IllegalArgumentException("无法安全解释 Trace 结构"); }
    }

    private record RawSpan(String id, String parent, String service, String operation, long start, long duration,
                           Map<String, JsonNode> tags, Long run, Long space) { }
}
