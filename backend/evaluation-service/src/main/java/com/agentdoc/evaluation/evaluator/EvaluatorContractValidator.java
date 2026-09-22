package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 校验首版内置 Evaluator 的固定配置契约，不接受目录外字段。
 * <p>
 * 负责校验内置评估器 configJson / expectedJson 的结构、字段白名单与业务约束；
 * 严格禁止传入不在白名单内的额外字段，保证配置契约稳定。
 * </p>
 */
@Component
public class EvaluatorContractValidator {
    /** text-assertion 断言最大条数上限，与执行引擎保持一致 */
    private static final int MAX_ASSERTIONS = 50;
    /** text-assertion 单条断言value字符串最大长度，与执行引擎保持一致 */
    private static final int MAX_ASSERTION_VALUE_LENGTH = 1000;

    /**
     * 校验评估器版本schema，首版只允许 configSchemaVersion=1、resultSchemaVersion=1
     *
     * @param evaluatorKey        内置评估器key
     * @param configSchemaVersion 配置schema版本号
     * @param configJson          评估器配置JSON
     * @param resultSchemaVersion 结果schema版本号
     */
    public void validateVersion(String evaluatorKey, Integer configSchemaVersion, String configJson,
                                Integer resultSchemaVersion) {
        // 首版固定只支持schema版本1，版本不匹配直接拒绝
        if (configSchemaVersion == null || configSchemaVersion != 1
                || resultSchemaVersion == null || resultSchemaVersion != 1) {
            throw invalid("首版 Evaluator 仅支持 config/result schema version 1");
        }
        // 校验configJson结构，config模式expected=false
        validate(evaluatorKey, configJson, false);
    }

    /**
     * 校验 expectedJson（预期规则配置）
     *
     * @param evaluatorKey 内置评估器key
     * @param expectedJson 预期规则JSON
     */
    public void validateExpected(String evaluatorKey, String expectedJson) {
        // expected=true，代表当前校验的是expectedJson，部分字段强制必填
        validate(evaluatorKey, expectedJson, true);
    }

    /**
     * 通用校验入口，根据evaluatorKey分发到对应校验逻辑
     *
     * @param evaluatorKey 内置评估器key
     * @param json         待校验json字符串（config / expected）
     * @param expected     true=expectedJson，false=configJson，区分必填规则
     */
    private void validate(String evaluatorKey, String json, boolean expected) {
        JsonNode value = object(json);
        switch (evaluatorKey) {
            // 无配置字段，只允许空对象
            case "task-terminal-status", "isolation-invariant", "audit-ledger-integrity" ->
                    requireFields(value, Set.of());
            case "artifact-contract" -> validateArtifact(value);
            case "text-assertion" -> validateTextAssertions(value, expected);
            case "document-change-validator" -> validateDocumentChange(value);
            default -> throw invalid("不支持的内置 Evaluator key");
        }
    }

    /**
     * artifact-contract 配置校验：字段白名单、数量范围、schema版本、requiredTypes数组
     * @param value 解析后的JsonNode
     */
    private void validateArtifact(JsonNode value) {
        // 仅允许这4个配置字段，多余字段直接报错
        requireFields(value, Set.of("minCount", "maxCount", "schemaVersion", "requiredTypes"));
        int min = integer(value, "minCount", 0);
        int max = integer(value, "maxCount", 100);
        int schema = integer(value, "schemaVersion", 1);
        // 业务范围校验：min>=0，max>=min，max上限100，schema版本>=1
        if (min < 0 || max < min || max > 100 || schema < 1) {
            throw invalid("artifact-contract 数量或 schemaVersion 无效");
        }
        JsonNode types = value.get("requiredTypes");
        if (types != null) {
            // requiredTypes必须是字符串数组，最多100项
            if (!types.isArray() || types.size() > 100) {
                throw invalid("requiredTypes 必须是最多 100 项的字符串数组");
            }
            Set<String> unique = new HashSet<>();
            types.forEach(item -> {
                // 每项必须是非空文本，不可重复
                if (!item.isTextual() || item.textValue().isBlank() || !unique.add(item.textValue())) {
                    throw invalid("requiredTypes 包含无效或重复项");
                }
            });
        }
    }

    /**
     * text-assertion 断言配置校验，区分config与expected模式
     * @param value 解析后的JsonNode
     * @param expected true=expectedJson，assertions强制必填
     */
    private void validateTextAssertions(JsonNode value, boolean expected) {
        requireFields(value, Set.of("assertions"));
        JsonNode assertions = value.get("assertions");
        if (assertions == null) {
            // expectedJson场景下assertions不能缺失；config场景允许为空
            if (expected) {
                throw invalid("text-assertion expectedJson 必须包含 assertions");
            }
            return;
        }
        // 断言数组非空且不超过最大条数
        if (!assertions.isArray() || assertions.isEmpty() || assertions.size() > MAX_ASSERTIONS) {
            throw invalid("assertions 必须包含 1~50 项");
        }
        assertions.forEach(assertion -> {
            // 单条断言必须是JSON对象
            if (!assertion.isObject()) {
                throw invalid("assertion 必须是 JSON object");
            }
            // 断言内部只允许field、operator、value三个字段
            requireFields(assertion, Set.of("field", "operator", "value"));
            String field = text(assertion, "field");
            String operator = text(assertion, "operator");
            String assertionValue = text(assertion, "value");
            // 校验：固定field、合法操作符、value长度、SHA256时哈希格式正则
            if (!"resultSummary".equals(field)
                    || !Set.of("CONTAINS", "NOT_CONTAINS", "EXACT", "SHA256").contains(operator)
                    || assertionValue == null || assertionValue.length() > MAX_ASSERTION_VALUE_LENGTH
                    || "SHA256".equals(operator) && !assertionValue.matches("[0-9a-f]{64}")) {
                throw invalid("text-assertion 断言契约无效");
            }
        });
    }

    /**
     * document-change-validator 配置校验：requiredArtifactType、targetContentSha256
     * @param value 解析后的JsonNode
     */
    private void validateDocumentChange(JsonNode value) {
        requireFields(value, Set.of("requiredArtifactType", "targetContentSha256"));
        String requiredType = text(value, "requiredArtifactType");
        String targetHash = text(value, "targetContentSha256");
        // 字段存在时做合法性校验：requiredType非空；targetHash存在则必须是64位小写sha256
        if (value.has("requiredArtifactType") && (requiredType == null || requiredType.isBlank())
                || value.has("targetContentSha256")
                && (targetHash == null || !targetHash.matches("[0-9a-f]{64}"))) {
            throw invalid("document-change-validator 配置无效");
        }
    }

    /**
     * 将json字符串转为JsonNode，空串返回空对象；必须是JSON Object
     * @param json 原始json字符串
     * @return JsonNode对象
     */
    private JsonNode object(String json) {
        JsonNode value = json == null || json.isBlank()
                ? JsonUtils.parse("{}", JsonNode.class) : JsonUtils.parse(json, JsonNode.class);
        if (value == null || !value.isObject()) {
            throw invalid("Evaluator 配置必须是 JSON object");
        }
        return value;
    }

    /**
     * 字段白名单校验：存在不在allowed集合内的字段则报错，禁止额外字段
     * @param value Json节点
     * @param allowed 允许的字段名集合
     */
    private void requireFields(JsonNode value, Set<String> allowed) {
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next().getKey())) {
                throw invalid("Evaluator 配置包含未声明字段");
            }
        }
    }

    /**
     * 读取整型字段，校验必须是整数，空返回默认值
     * @param value Json节点
     * @param field 字段名
     * @param defaultValue 默认值
     * @return int值
     */
    private int integer(JsonNode value, String field, int defaultValue) {
        JsonNode node = value.get(field);
        if (node == null) {
            return defaultValue;
        }
        // 校验节点为整数类型，可安全转为int
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(field + " 必须是整数");
        }
        return node.intValue();
    }

    /**
     * 读取文本字段，非文本返回null
     * @param value Json节点
     * @param field 字段名
     * @return 文本字符串
     */
    private String text(JsonNode value, String field) {
        JsonNode node = value.get(field);
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    /**
     * 构造参数非法业务异常
     * @param message 错误提示信息
     * @return BusinessException
     */
    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
