package com.agentdoc.evaluation.evaluator;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** 新线上规则只注册契约；LIVE 原始证据引擎在 P6-03 完成。 */
public final class OnlineRuleContractValidator {
    public static final String TEXT = "online-original-text-assertion";
    public static final String CHANGE = "online-document-change-validator";
    private static final int MAX_ASSERTIONS = 50;
    private static final int MAX_VALUE_CODE_POINTS = 2000;

    private OnlineRuleContractValidator() { }
    public static boolean onlineOnly(String key) { return TEXT.equals(key) || CHANGE.equals(key); }

    public static void validate(String key, String json, boolean expected) {
        JsonNode value;
        try { value = OnlineProtocolUtils.object(json); }
        catch (IllegalArgumentException invalid) { throw invalid(); }
        if (!expected) { fields(value, Set.of()); return; }
        if (TEXT.equals(key)) {
            fields(value, Set.of("assertions"));
            var assertions = value.get("assertions");
            if (assertions == null || !assertions.isArray() || assertions.isEmpty() || assertions.size() > MAX_ASSERTIONS) {
                throw invalid();
            }
            assertions.forEach(assertion -> {
                fields(assertion, Set.of("field", "operator", "value"));
                String operator = text(assertion, "operator");
                String content = text(assertion, "value");
                if (!"originalText".equals(text(assertion, "field")) || operator == null
                        || !Set.of("CONTAINS", "NOT_CONTAINS", "EXACT", "SHA256").contains(operator)
                        || content == null || content.codePointCount(0, content.length()) > MAX_VALUE_CODE_POINTS
                        || "SHA256".equals(operator) && !content.matches("[0-9a-f]{64}")) { throw invalid(); }
            });
        } else if (CHANGE.equals(key)) {
            fields(value, Set.of("requiredOutputType", "targetContentSha256"));
            String type = text(value, "requiredOutputType");
            if (type == null || !Set.of("CHANGE_REQUEST", "DRAFT_DOCUMENT").contains(type)
                    || value.has("targetContentSha256")
                    && (text(value, "targetContentSha256") == null
                    || !text(value, "targetContentSha256").matches("[0-9a-f]{64}"))) { throw invalid(); }
        } else { throw invalid(); }
    }

    private static void fields(JsonNode value, Set<String> names) {
        if (value == null || !value.isObject()) { throw invalid(); }
        value.fieldNames().forEachRemaining(name -> { if (!names.contains(name)) { throw invalid(); } });
    }
    private static String text(JsonNode value, String name) {
        var field = value.get(name);
        return field != null && field.isTextual() ? field.textValue() : null;
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BAD_REQUEST, ONLINE_RULE_INVALID.name()); }
}
