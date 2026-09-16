package com.agentdoc.common.logging;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 单次日志或外部连接使用的不可变敏感字段策略。
 * <p>
 * 用于判断字段是否属于敏感字段，提供默认敏感关键字集合，支持追加会话级动态敏感key。
 * 策略对象不可变；withSensitiveKeys 会返回新实例，不会修改原有实例。
 * 字段名会统一标准化：小写、将连字符/下划线/点统一替换为点，用于匹配。
 * </p>
 */
public final class SensitiveFieldPolicy {

    /**
     * 默认敏感字段名集合，标准化后的key。
     * 包含认证头、密钥、token、密码、凭证、私钥、权限令牌等关键字段。
     */
    private static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
            "authorization", "proxy.authorization", "proxyauthorization", "cookie", "set.cookie",
            "setcookie", "x.api.key", "xapikey", "api.key", "apikey", "token", "access.token",
            "accesstoken", "refresh.token", "refreshtoken", "id.token", "idtoken", "secret",
            "client.secret", "clientsecret", "password", "passwd", "credential", "private.key",
            "privatekey", "capability");

    /**
     * 白名单数字字段：即使命中名字规则，只要值是Number类型就不脱敏。
     * 多用于token消耗计数、数量类指标，例如 input.tokens / output.tokens。
     */
    private static final Set<String> ALLOWED_NUMERIC_KEYS = Set.of(
            "input.tokens", "output.tokens", "cached.input.tokens", "total.tokens",
            "gen.ai.usage.input.tokens", "gen.ai.usage.output.tokens",
            "capability.count", "secret.count");

    /**
     * 内容类字段集合：data/payload/content/body/prompt/messages 等，默认标记为敏感。
     * 这类字段通常承载业务原始报文、提示词、消息体，需要日志脱敏。
     */
    private static final Set<String> CONTENT_KEYS = Set.of(
            "data", "payload", "content", "body", "prompt", "completion", "instruction",
            "arguments", "messages", "diff");

    /**
     * 会话动态敏感key集合；实例不可变，构造时做不可变拷贝。
     */
    private final Set<String> dynamicSensitiveKeys;

    /**
     * 私有构造，由静态工厂方法创建实例。
     * @param dynamicSensitiveKeys 会话级动态敏感key集合
     */
    private SensitiveFieldPolicy(Set<String> dynamicSensitiveKeys) {
        this.dynamicSensitiveKeys = Set.copyOf(dynamicSensitiveKeys);
    }

    /**
     * 获取默认策略实例：无额外动态敏感key，仅使用内置 DEFAULT_SENSITIVE_KEYS。
     * @return 默认脱敏策略对象
     */
    public static SensitiveFieldPolicy defaults() {
        return new SensitiveFieldPolicy(Set.of());
    }

    /**
     * 返回登记了当前会话动态认证字段的新策略，不修改原策略。
     * <p>
     * 传入keys会做标准化处理（normalize），过滤null/空白字符串。
     * 原有策略保持不变，新策略 = 原有动态key + 新增key。
     * </p>
     * @param keys 追加的敏感字段名集合，可以为null
     * @return 全新的 SensitiveFieldPolicy 实例
     */
    public SensitiveFieldPolicy withSensitiveKeys(Collection<String> keys) {
        HashSet<String> merged = new HashSet<>(dynamicSensitiveKeys);
        if (keys != null) {
            keys.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(SensitiveFieldPolicy::normalize)
                    .forEach(merged::add);
        }
        return new SensitiveFieldPolicy(merged);
    }

    /**
     * 判断指定key+value是否需要脱敏。
     * <p>
     * 判断逻辑顺序：
     * 1. key标准化后为空，直接返回 false（不敏感）
     * 2. 如果是白名单数字key，并且value是Number类型，返回 false（跳过脱敏）
     * 3. 命中【动态敏感key / 默认敏感key / 内容字段】任一，返回 true
     * 4. key以 .tokens 结尾，返回 true
     * 5. 排除项：.secret.count / .capability.count / secret.count / capability.count 返回 false
     * </p>
     * @param key 原始字段名称
     * @param value 字段值对象
     * @return true=需要脱敏；false=无需脱敏
     */
    public boolean isSensitive(String key, Object value) {
        String normalized = normalize(key);
        if (normalized.isEmpty())
            return false;
        if (ALLOWED_NUMERIC_KEYS.contains(normalized) && value instanceof Number)
            return false;
        if (matches(normalized, dynamicSensitiveKeys) || matches(normalized, DEFAULT_SENSITIVE_KEYS)
                || matches(normalized, CONTENT_KEYS))
            return true;
        if (normalized.endsWith(".tokens"))
            return true;
        return normalized.endsWith(".secret.count") || normalized.endsWith(".capability.count")
                || normalized.equals("secret.count") || normalized.equals("capability.count");
    }

    /**
     * 字段名标准化：转为ROOT小写，把 - _ . 统一替换成单个点，去除首尾空白。
     * <p>
     * 示例：x-api-key -> x.api.key；proxy_authorization -> proxy.authorization
     * </p>
     * @param key 原始字段名
     * @return 标准化后的字符串，null返回空串
     */
    static String normalize(String key) {
        if (key == null)
            return "";
        return key.toLowerCase(Locale.ROOT)
                .replaceAll("[-_.]+", ".")
                .trim();
    }

    /**
     * 匹配逻辑：标准化key完全相等，或者以 .候选key 结尾。
     * <p>
     * 例：header.authorization 匹配 authorization；request.token 匹配 token。
     * </p>
     * @param normalized 标准化后的字段key
     * @param candidates 候选敏感key集合
     * @return true：命中匹配规则
     */
    private static boolean matches(String normalized, Set<String> candidates) {
        return candidates.stream()
                .anyMatch(candidate ->
                        normalized.equals(candidate)
                        || normalized.endsWith("." + candidate));
    }
}