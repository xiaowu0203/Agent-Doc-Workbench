package com.agentdoc.common.logging;

import com.agentdoc.common.utils.JsonUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化日志写出前的统一脱敏入口。
 * <p>
 * 提供多层脱敏能力：对象/Map 字段敏感判断、JSON 字符串解析脱敏、自由文本正则清洗、URL 脱敏、异常堆栈脱敏。
 * 内置规则：Bearer/Basic Auth、JWT、PEM私钥、敏感key=value键值对、HTTP链接；支持自定义脱敏策略 SensitiveFieldPolicy。
 * 所有对外静态方法线程安全，禁止实例化。
 * </p>
 */
public final class LogSanitizer {

    /** 脱敏替换占位符 */
    public static final String REDACTED = "[REDACTED]";

    /** 默认敏感字段策略 */
    private static final SensitiveFieldPolicy DEFAULT_POLICY = SensitiveFieldPolicy.defaults();

    /**
     * 匹配 Authorization: Bearer xxx / Basic xxx 认证头值，忽略大小写
     * 匹配：Bearer token、Basic base64串
     */
    private static final Pattern AUTH_VALUE = Pattern.compile(
            "(?i)\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");

    /**
     * JWT 正则匹配，避免把普通eyJ开头字符串误杀，使用负向先行断言
     * 格式：header.payload.signature
     */
    private static final Pattern JWT_VALUE = Pattern.compile(
            "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    /**
     * 多行模式匹配完整PEM私钥块，包括 BEGIN/END 标记和中间内容
     */
    private static final Pattern PEM_PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----");

    /**
     * 敏感键值对正则：匹配常见密钥/密码类key，捕获 key 和后面的值部分
     * 支持分隔符 : / =，key支持连字符、下划线、点，忽略大小写
     * 匹配示例：password=123、Authorization: Bearer xxx、api-key: abc
     */
    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)\\b(authorization|proxy[-_.]?authorization|cookie|set[-_.]?cookie|x[-_.]?api[-_.]?key|"
                    + "api[-_.]?key|access[-_.]?token|refresh[-_.]?token|id[-_.]?token|token|"
                    + "client[-_.]?secret|secret|password|passwd|credential|private[-_.]?key|capability)"
                    + "\\s*[:=]\\s*([^\\s,;}]+)");

    /** HTTP/HTTPS URL 匹配，用于后续URL脱敏处理 */
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s<>\\\"']+");

    private LogSanitizer() {
    }

    /**
     * 字段脱敏，使用默认敏感策略
     *
     * @param key   字段名称
     * @param value 字段原始值
     * @return 脱敏后的值，敏感字段直接返回 REDACTED
     */
    public static Object sanitizeField(String key, Object value) {
        return sanitizeField(key, value, DEFAULT_POLICY);
    }

    /**
     * 单个字段脱敏核心方法
     * <p>
     * 1. 使用传入策略判断字段是否敏感，敏感直接返回掩码
     * 2. 非敏感则递归对value内部对象/集合做清洗
     * </p>
     *
     * @param key     字段名
     * @param value   原始字段值
     * @param policy  敏感字段策略，null 自动降级为默认策略
     * @return 脱敏后对象
     */
    public static Object sanitizeField(String key, Object value, SensitiveFieldPolicy policy) {
        SensitiveFieldPolicy effectivePolicy = policy == null ? DEFAULT_POLICY : policy;
        if (effectivePolicy.isSensitive(key, value)) return REDACTED;
        return sanitizeObject(value, effectivePolicy);
    }

    /**
     * Map 批量脱敏，遍历所有K-V，对每个字段执行脱敏
     * 返回不可变 LinkedHashMap，保持原有顺序
     *
     * @param values 原始Map
     * @param policy 敏感策略，null使用默认策略
     * @return 脱敏后的不可变Map；入参null返回空不可变Map
     */
    public static Map<String, Object> sanitizeMap(Map<String, ?> values, SensitiveFieldPolicy policy) {
        if (values == null) return Map.of();
        SensitiveFieldPolicy effectivePolicy = policy == null ? DEFAULT_POLICY : policy;
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> sanitized.put(key, sanitizeField(key, value, effectivePolicy)));
        return Collections.unmodifiableMap(sanitized);
    }

    /**
     * JSON字符串脱敏：先解析成对象，递归脱敏后再序列化为JSON
     * <p>
     * 解析失败/解析结果为空时，降级使用纯文本兜底清洗 {@link #sanitizeText(String)}
     * </p>
     *
     * @param json   原始JSON字符串
     * @param policy 敏感策略
     * @return 脱敏后的JSON字符串
     */
    public static String sanitizeJson(String json, SensitiveFieldPolicy policy) {
        Object parsed = JsonUtils.parse(json, Object.class);
        return parsed == null ? sanitizeText(json) : JsonUtils.toJson(sanitizeObject(parsed,
                policy == null ? DEFAULT_POLICY : policy));
    }

    /**
     * URL脱敏：移除 userinfo、query参数、fragment，只保留 scheme://host[:port]/path
     * <p>
     * 非法URI直接返回掩码；opaque非分层URI只保留scheme+掩码
     * </p>
     *
     * @param value 原始URL字符串
     * @return 脱敏后的URL；null/空白直接原值返回
     */
    public static String sanitizeUrl(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            URI uri = new URI(value);
            if (uri.isOpaque()) return uri.getScheme() == null ? REDACTED : uri.getScheme() + ":" + REDACTED;
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
                    .toString();
        } catch (URISyntaxException exception) {
            return REDACTED;
        }
    }

    /**
     * 自由文本兜底清洗（纯字符串正则替换）
     * 顺序：PEM私钥 → URL脱敏 → Auth认证串 → JWT令牌 → 敏感键值对
     * <p>
     * 适用于无法结构化解析的日志文本、message、异常栈等
     * </p>
     *
     * @param value 原始文本
     * @return 脱敏文本；null/空串直接返回原值
     */
    public static String sanitizeText(String value) {
        if (value == null || value.isEmpty()) return value;
        String sanitized = PEM_PRIVATE_KEY.matcher(value).replaceAll(REDACTED);
        Matcher urls = HTTP_URL.matcher(sanitized);
        StringBuffer result = new StringBuffer();
        while (urls.find()) {
            urls.appendReplacement(result, Matcher.quoteReplacement(sanitizeUrl(urls.group())));
        }
        urls.appendTail(result);
        sanitized = AUTH_VALUE.matcher(result.toString()).replaceAll(REDACTED);
        sanitized = JWT_VALUE.matcher(sanitized).replaceAll(REDACTED);
        return SENSITIVE_PAIR.matcher(sanitized).replaceAll("$1=" + REDACTED);
    }

    /**
     * 异常堆栈脱敏：打印完整异常栈，然后对栈文本做脱敏清洗
     * 保留异常类型、调用栈结构，清洗 message 内的令牌、密钥、URL等敏感信息
     *
     * @param throwable 原始异常对象
     * @return 脱敏后的异常堆栈字符串；入参null返回null
     */
    public static String sanitizeThrowable(Throwable throwable) {
        if (throwable == null) return null;
        StringWriter output = new StringWriter();
        throwable.printStackTrace(new PrintWriter(output));
        return sanitizeText(output.toString());
    }

    /**
     * 递归对象脱敏内部方法
     * <ul>
     *     <li>Map：遍历entry，递归调用sanitizeField</li>
     *     <li>Iterable / 数组：遍历元素递归脱敏</li>
     *     <li>String：调用sanitizeText文本清洗</li>
     *     <li>其他基础类型：直接返回原值</li>
     * </ul>
     *
     * @param value 待脱敏对象
     * @param policy 敏感策略
     * @return 脱敏后的对象
     */
    private static Object sanitizeObject(Object value, SensitiveFieldPolicy policy) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                String name = String.valueOf(key);
                sanitized.put(name, sanitizeField(name, nested, policy));
            });
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            iterable.forEach(item -> sanitized.add(sanitizeObject(item, policy)));
            return sanitized;
        }
        if (value instanceof Object[] array) {
            List<Object> sanitized = new ArrayList<>(array.length);
            for (Object item : array) sanitized.add(sanitizeObject(item, policy));
            return sanitized;
        }
        if (value instanceof String string) return sanitizeText(string);
        return value;
    }
}