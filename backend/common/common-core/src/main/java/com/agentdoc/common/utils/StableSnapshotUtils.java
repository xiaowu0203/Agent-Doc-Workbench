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
 * 稳定快照工具类
 * <p>
 * 用于生成**可复现、稳定的快照哈希值**。
 * 核心目标：相同业务数据，无论对象字段定义顺序、Map 插入顺序如何，输出的 JSON 序列化结果一致，最终 SHA‑256 哈希相同。
 * 约束：
 * 1. 对象/Map 的 key 强制字典序排序
 * 2. 数组元素顺序保持原样，由调用方保证数组顺序稳定
 * 3. null 值会保留序列化输出，不会被过滤
 * 4. 序列化后使用 UTF‑8 字节计算 SHA‑256 十六进制哈希
 * </p>
 */
public final class StableSnapshotUtils {

    /**
     * Jackson 序列化配置：用于生成规范的规范化JSON
     * - SORT_PROPERTIES_ALPHABETICALLY：POJO对象字段按字母排序
     * - ORDER_MAP_ENTRIES_BY_KEYS：Map 条目按key排序
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    /**
     * 私有构造，工具类禁止实例化
     */
    private StableSnapshotUtils() {
    }

    /**
     * 生成快照稳定哈希
     * <p>
     * 封装 envelope 包裹对象：{schemaVersion: 版本号, snapshot: 快照原始对象}
     * 经过规范化JSON处理后，计算UTF‑8字节的SHA‑256十六进制哈希。
     * </p>
     * @param schemaVersion 快照schema版本号，用于区分不同版本快照格式
     * @param snapshot 待做快照的业务对象，可以是任意对象(Map/POJO/集合等)
     * @return SHA‑256小写十六进制哈希字符串
     */
    public static String snapshotHash(int schemaVersion, Object snapshot) {
        // 使用TreeMap保证外层envelope的key天然字典序
        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("schemaVersion", schemaVersion);
        envelope.put("snapshot", snapshot);
        return sha256Utf8(toCanonicalJson(envelope));
    }

    /**
     * 对字符串做UTF‑8编码，计算SHA‑256摘要，返回小写十六进制结果
     * <p>
     * 入参为null时，当作空字符串处理。
     * </p>
     * @param value 待哈希的文本
     * @return SHA‑256 十六进制小写字符串
     * @throws IllegalStateException JDK不支持SHA‑256算法时抛出
     */
    public static String sha256Utf8(String value) {
        try {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /**
     * 将对象转为**规范化标准JSON字符串**
     * <p>
     * 递归处理JsonNode：对象key强制字典序排序；数组保持原有顺序；null、基础值节点原样保留。
     * 注意：Jackson本身的配置只能处理POJO/Map，嵌套的ObjectNode需要手动递归排序。
     * </p>
     * @param value 任意待序列化对象
     * @return 规范化JSON字符串
     * @throws IllegalStateException 序列化异常时抛出
     */
    static String toCanonicalJson(Object value) {
        try {
            // 先转为JsonNode树模型，再做递归规范化，最后输出json字符串
            return MAPPER.writeValueAsString(canonicalNode(MAPPER.valueToTree(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("快照规范化失败", exception);
        }
    }

    /**
     * 递归规范化JsonNode节点
     * <ul>
     * <li>null / 基础值节点：直接返回原节点</li>
     * <li>数组节点：遍历每个元素递归规范化，数组内部顺序不变</li>
     * <li>对象节点：字段名排序后，依次递归处理每个字段值，重新构建ObjectNode</li>
     * </ul>
     * @param node 原始JsonNode
     * @return 规范化后的JsonNode
     */
    private static JsonNode canonicalNode(JsonNode node) {
        // 空节点、null、基础值（字符串、数字、布尔）直接返回
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        // 数组：保持原有顺序，每个子元素递归规范化
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            node.forEach(item -> result.add(canonicalNode(item)));
            return result;
        }
        // 对象节点：字段名排序，递归处理每个value
        ObjectNode result = MAPPER.createObjectNode();
        StreamSupport.stream(((Iterable<String>) node::fieldNames).spliterator(), false)
                .sorted() // 字段名字典序排序
                .forEach(name -> result.set(name, canonicalNode(node.get(name))));
        return result;
    }
}