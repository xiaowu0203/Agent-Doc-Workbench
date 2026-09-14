package com.agentdoc.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

/**
 * 不可变快照的规范化 JSON 与 SHA-256 工具。
 */
public final class StableSnapshotUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private StableSnapshotUtils() {
    }

    /**
     * 将 schema 版本与快照内容共同规范化并计算 SHA-256。
     * 对象键按字典序排列，显式 null 保留；数组顺序由调用领域在传入前固定。
     */
    public static String snapshotHash(int schemaVersion, Object snapshot) {
        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("schemaVersion", schemaVersion);
        envelope.put("snapshot", snapshot);
        return sha256Utf8(toCanonicalJson(envelope));
    }

    /**
     * 计算 UTF-8 文本的 SHA-256；null 按空字符串处理。
     */
    public static String sha256Utf8(String value) {
        try {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    static String toCanonicalJson(Object value) {
        try {
            return MAPPER.writeValueAsString(canonicalNode(MAPPER.valueToTree(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("快照规范化失败", exception);
        }
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            node.forEach(item -> result.add(canonicalNode(item)));
            return result;
        }
        ObjectNode result = MAPPER.createObjectNode();
        StreamSupport.stream(((Iterable<String>) node::fieldNames).spliterator(), false)
                .sorted()
                .forEach(name -> result.set(name, canonicalNode(node.get(name))));
        return result;
    }
}
