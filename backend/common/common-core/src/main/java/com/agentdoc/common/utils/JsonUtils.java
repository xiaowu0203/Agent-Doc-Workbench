package com.agentdoc.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 序列化工具（通用；线程安全——内部共享 ObjectMapper）。
 * <p>供各实体 / Service 做 JSON ↔ 对象的纯转换，避免各自持有 ObjectMapper。</p>
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    /**
     * 对象序列化为 JSON 字符串。
     * @param value 任意可序列化对象
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败（编程错误）
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * JSON 字符串解析为对象（按类型）。
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 目标对象；json 为空或解析失败返回 null
     */
    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * JSON 字符串解析为对象（支持泛型，如 {@code List<T>}）。
     * @param json JSON 字符串
     * @param typeReference 目标类型引用（如 {@code new TypeReference<List<X>>() {}}）
     * @param <T> 目标类型
     * @return 目标对象；json 为空或解析失败返回 null
     */
    public static <T> T parse(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
