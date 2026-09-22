package com.agentdoc.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Execution Snapshot v3 快照规范化工具类
 * <p>
 * 用于生成 v3 版本标准规范的快照 JSON 结构，保证相同业务快照数据输出唯一的序列化字符串，
 * 用于后续稳定哈希计算；旧版本快照 schema 禁止调用本类进行重算。
 * 核心规则：对象按键名UTF-8无符号字节序排序、浮点数统一转为十进制无指数字符串、数组保持原有顺序。
 * </p>
 */
public final class SnapshotCanonicalV3Utils {

    /** JSON序列化器，用于构建与转换JsonNode */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 字段名排序比较器：按照UTF-8原始字节无符号顺序排序，保证JSON对象key顺序固定 */
    private static final Comparator<String> UTF8_UNSIGNED_ORDER = SnapshotCanonicalV3Utils::compareUtf8;

    /** 私有构造，工具类禁止实例化 */
    private SnapshotCanonicalV3Utils() {
    }

    /**
     * 生成带schemaVersion包装层的规范化Envelope JSON
     * <p>
     * 外层固定结构 { "schemaVersion":版本号, "snapshot":快照主体 }，内部会递归规范化所有节点。
     * </p>
     * @param schemaVersion 快照协议版本号
     * @param snapshot 快照业务实体对象
     * @return 规范化后的JSON字符串
     * @throws IllegalStateException JSON序列化异常时抛出
     */
    public static String canonicalEnvelope(int schemaVersion, Object snapshot) {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("schemaVersion", schemaVersion);
        envelope.set("snapshot", MAPPER.valueToTree(snapshot));
        try {
            return MAPPER.writeValueAsString(canonicalNode(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("v3 快照规范化失败", exception);
        }
    }

    /**
     * 对已经完成规范化的envelope字符串计算UTF-8编码SHA-256哈希
     * @param canonicalEnvelope 已规范化的envelope JSON字符串
     * @return SHA-256十六进制哈希字符串
     */
    public static String hashEnvelope(String canonicalEnvelope) {
        return StableSnapshotUtils.sha256Utf8(canonicalEnvelope);
    }

    /**
     * BigDecimal十进制规范化输出
     * <p>协议约束：禁止科学计数指数形式；所有零值统一输出为0，去除末尾多余的尾随零</p>
     * @param value 原始BigDecimal数值
     * @return 规范化十进制字符串，输入null返回null
     */
    public static String decimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return normalized.toPlainString();
    }

    /**
     * 递归规范化单个JsonNode节点
     * <ul>
     * <li>基础类型：null/布尔/文本/整数直接保留</li>
     * <li>浮点数值：转为BigDecimal，使用decimal()输出十进制字符串</li>
     * <li>数组：保持原有顺序，递归规范化每个子元素</li>
     * <li>对象：字段名按UTF-8无符号字节排序，递归规范化字段值</li>
     * </ul>
     * @param node 待规范化JsonNode
     * @return 规范化后的JsonNode
     * @throws IllegalArgumentException 遇到不支持的JSON节点类型抛出异常
     */
    private static JsonNode canonicalNode(JsonNode node) {
        if (node == null || node.isNull() || node.isIntegralNumber() || node.isBoolean() || node.isTextual()) {
            return node;
        }
        if (node.isFloatingPointNumber()) {
            return TextNode.valueOf(decimal(node.decimalValue()));
        }
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            node.forEach(item -> result.add(canonicalNode(item)));
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = MAPPER.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(UTF8_UNSIGNED_ORDER);
            names.forEach(name -> result.set(name, canonicalNode(node.get(name))));
            return result;
        }
        throw new IllegalArgumentException("v3 快照包含不支持的 JSON 节点: " + node.getNodeType());
    }

    /**
     * UTF-8无符号字节序字符串比较
     * 用于JSON对象字段名排序，保证跨平台、跨语言稳定一致的key排序规则
     * @param left 左字符串
     * @param right 右字符串
     * @return 比较结果：小于0 / 等于0 / 大于0
     */
    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < length; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }
}
