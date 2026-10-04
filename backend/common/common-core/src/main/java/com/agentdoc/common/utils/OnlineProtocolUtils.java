package com.agentdoc.common.utils;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** 线上 schema 2 协议；不重算既有 execution/input snapshot。 */
public final class OnlineProtocolUtils {
    public static final int SCHEMA_VERSION = 2;
    public static final int BUCKET_COUNT = 10000;

    private OnlineProtocolUtils() { }

    public static String canonical(String domain, Object payload) {
        return canonicalNode(JsonUtils.parseStrict(JsonUtils.toJson(
                Map.of("domain", domain, "schemaVersion", SCHEMA_VERSION, "payload", payload)), JsonNode.class));
    }

    public static String hash(String domain, Object payload) {
        return StableSnapshotUtils.sha256Utf8(canonical(domain, payload));
    }

    /** 严格解析用于协议的对象，拒绝重复字段、浮点与非法 Unicode。 */
    public static JsonNode object(String json) {
        JsonNode node = JsonUtils.parseStrict(json, JsonNode.class);
        if (node == null || !node.isObject()) { throw new IllegalArgumentException(MANIFEST_INVALID.name()); }
        canonicalNode(node);
        return node;
    }

    public static String canonicalNode(JsonNode node) {
        if (node == null) { throw new IllegalArgumentException(MANIFEST_INVALID.name()); }
        if (node.isTextual()) { requireUnicode(node.textValue()); }
        if (node.isNull() || node.isBoolean() || node.isIntegralNumber() || node.isTextual()) {
            return JsonUtils.toJson(node);
        }
        if (node.isArray()) {
            var array = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> array.add(JsonUtils.parseStrict(canonicalNode(value), JsonNode.class)));
            return JsonUtils.toJson(array);
        }
        if (node.isObject()) {
            var object = JsonNodeFactory.instance.objectNode();
            var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(OnlineProtocolUtils::requireUnicode);
            names.sort((a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),
                    b.getBytes(StandardCharsets.UTF_8)));
            names.forEach(name -> object.set(name, JsonUtils.parseStrict(canonicalNode(node.get(name)), JsonNode.class)));
            return JsonUtils.toJson(object);
        }
        throw new IllegalArgumentException(MANIFEST_INVALID.name());
    }

    public static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException(ID_INVALID.name());
        }
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(ID_INVALID.name()); }
    }

    public static int bucket(String experimentId, String documentId, String seed) {
        id(experimentId);
        id(documentId);
        if (seed == null || !seed.matches("[0-9a-f]{32}")) { throw new IllegalArgumentException(SEED_INVALID.name()); }
        return new BigInteger(hash("online.bucket", Map.of("experimentId", experimentId,
                "documentId", documentId, "seed", seed)), 16).mod(BigInteger.valueOf(BUCKET_COUNT)).intValue();
    }

    public static String variant(int bucket, int weight) {
        if (bucket < 0 || bucket >= BUCKET_COUNT || weight < 1 || weight >= BUCKET_COUNT) {
            throw new IllegalArgumentException(BUCKET_INVALID.name());
        }
        return bucket < weight ? "CANDIDATE" : "BASELINE";
    }

    private static void requireUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException(UNICODE_INVALID.name());
                }
            } else if (Character.isLowSurrogate(current)) { throw new IllegalArgumentException(UNICODE_INVALID.name()); }
        }
    }
}
