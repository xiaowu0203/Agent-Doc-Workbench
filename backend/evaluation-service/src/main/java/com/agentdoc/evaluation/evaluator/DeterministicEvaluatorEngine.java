package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.EvaluationArtifactEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.metric.StandardMetricOutput;
import com.agentdoc.evaluation.metric.StandardMetricValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 六个固定规则的确定性评估器实现，不加载脚本、表达式或用户自定义类。
 * <p>
 * 内置原生评估规则引擎，实现6种预定义内置评估器；
 * 所有逻辑纯内存计算，无动态脚本，输出标准化 DeterministicEvaluationOutcome，包含判定状态、指标与证据引用。
 * </p>
 */
@Component
public class DeterministicEvaluatorEngine {
    /** text-assertion 断言最大条数上限，防止配置过大 */
    private static final int MAX_ASSERTIONS = 50;
    /** text-assertion 单条断言值字符串最大长度，防止超长文本攻击 */
    private static final int MAX_ASSERTION_VALUE_LENGTH = 1000;

    /**
     * 评估入口，无评估器配置JSON（configJson默认空对象）
     *
     * @param evaluatorKey 内置评估器唯一key
     * @param expectedJson 评估预期规则JSON
     * @param evidence     评估证据包
     * @return 确定性评估输出结果
     */
    public DeterministicEvaluationOutcome evaluate(String evaluatorKey, String expectedJson,
                                                   EvaluationEvidenceBundleVO evidence) {
        // 重载入口，默认config为空对象
        return evaluate(evaluatorKey, "{}", expectedJson, evidence);
    }

    /**
     * 评估主入口，路由到对应内置评估规则
     *
     * @param evaluatorKey 内置评估器唯一key
     * @param configJson   评估器配置JSON
     * @param expectedJson 评估预期规则JSON
     * @param evidence     评估证据包
     * @return 确定性评估输出结果
     * @apiNote 合并configJson与expectedJson，expected字段会覆盖config；仅支持预定义6种内置evaluatorKey
     */
    public DeterministicEvaluationOutcome evaluate(String evaluatorKey, String configJson, String expectedJson,
                                                   EvaluationEvidenceBundleVO evidence) {
        return evaluate(evaluatorKey, configJson, expectedJson, evidence, List.of());
    }

    public DeterministicEvaluationOutcome evaluate(String evaluatorKey, String configJson, String expectedJson,
                                                   EvaluationEvidenceBundleVO evidence,
                                                   List<EvaluationDocumentChangeEvidenceVO> documentChanges) {
        // 基础入参校验
        if (evaluatorKey == null || evidence == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evaluator 输入不完整");
        }
        // 拷贝config作为基础配置
        ObjectNode expected = object(configJson).deepCopy();
        // expectedJson覆盖config中的同名字段
        object(expectedJson).fields().forEachRemaining(field -> expected.set(field.getKey(), field.getValue()));
        // 根据评估器key路由到对应规则实现
        return switch (evaluatorKey) {
            case "task-terminal-status" -> taskTerminal(evidence);
            case "artifact-contract" -> artifactContract(expected, evidence);
            case "text-assertion" -> textAssertion(expected, evidence);
            case "document-change-validator" -> documentChange(expected, evidence, documentChanges);
            case "isolation-invariant" -> isolation(evidence);
            case "audit-ledger-integrity" -> auditLedger(evidence);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的内置 Evaluator key");
        };
    }

    /**
     * task-terminal-status：校验任务与执行均达到COMPLETED终态
     * @param input 证据包
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome taskTerminal(EvaluationEvidenceBundleVO input) {
        // 校验任务状态、执行状态全部为COMPLETED
        boolean success = "COMPLETED".equals(input.taskStatus()) && "COMPLETED".equals(input.executionStatus());
        // 构建任务证据引用
        EvidenceReferenceValue task = taskEvidence(input);
        // 封装布尔型评估结果
        return boolOutcome(success, "TASK_TERMINAL", "evaluation.task-terminal.success", task);
    }

    /**
     * artifact-contract：校验产出物数量范围、必需类型、schema版本与sha256元数据完整性
     * @param expected 合并后的预期配置JsonNode
     * @param input 证据包
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome artifactContract(JsonNode expected,
                                                            EvaluationEvidenceBundleVO input) {
        // 读取制品数量上下限
        int minCount = integer(expected, "minCount", 0);
        int maxCount = integer(expected, "maxCount", 100);
        // 读取要求的schema版本
        int schemaVersion = integer(expected, "schemaVersion", 1);
        // 获取配置要求必须存在的制品类型集合
        Set<String> requiredTypes = strings(expected.get("requiredTypes"));
        // 提取实际返回的制品类型集合
        Set<String> actualTypes = input.artifacts().stream().map(EvaluationArtifactEvidenceVO::artifactType)
                .collect(java.util.stream.Collectors.toSet());
        // 校验所有制品元数据：schema版本、sha256长度、非空校验
        boolean metadataValid = input.artifacts().stream().allMatch(artifact -> artifact.schemaVersion() != null
                && artifact.schemaVersion() == schemaVersion && artifact.payloadSha256() != null
                && artifact.payloadSha256().length() == 64 && artifact.artifactType() != null);
        // 综合判定：数量区间 + 包含全部必需类型 + 元数据合法
        boolean valid = input.artifacts().size() >= minCount && input.artifacts().size() <= maxCount
                && actualTypes.containsAll(requiredTypes) && metadataValid;
        // 构建制品证据引用列表
        List<EvidenceReferenceValue> evidence = artifactEvidence(input);
        return boolOutcome(valid, "ARTIFACT_CONTRACT", "evaluation.artifact-contract.valid", evidence);
    }

    /**
     * text-assertion：文本断言校验，支持CONTAINS/NOT_CONTAINS/EXACT/SHA256，输出断言通过率指标
     * @param expected 合并后的预期配置JsonNode
     * @param input 证据包
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome textAssertion(JsonNode expected,
                                                         EvaluationEvidenceBundleVO input) {
        // 取出断言数组
        JsonNode assertions = expected.get("assertions");
        // 校验断言数组合法性：非空、数组、不超过最大条数
        if (assertions == null || !assertions.isArray() || assertions.isEmpty()
                || assertions.size() > MAX_ASSERTIONS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "text-assertion assertions 必须包含 1~50 项");
        }
        // 取实际文本，空则置空字符串
        String actual = input.resultSummary() == null ? "" : input.resultSummary();
        int passed = 0;
        List<Integer> failedIndexes = new ArrayList<>();
        // 逐条执行断言
        for (int index = 0; index < assertions.size(); index++) {
            JsonNode assertion = assertions.get(index);
            String field = text(assertion, "field");
            String operator = text(assertion, "operator");
            String value = text(assertion, "value");
            // 仅支持resultSummary字段，并且断言值长度校验
            if (!"resultSummary".equals(field) || value == null
                    || value.length() > MAX_ASSERTION_VALUE_LENGTH) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "text-assertion 字段或断言值不合法");
            }
            // 根据操作符执行文本匹配
            boolean matched = switch (operator == null ? "" : operator) {
                case "CONTAINS" -> actual.contains(value);
                case "NOT_CONTAINS" -> !actual.contains(value);
                case "EXACT" -> actual.equals(value);
                case "SHA256" -> StableSnapshotUtils.sha256Utf8(actual).equals(value);
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "text-assertion 操作符不受支持");
            };
            if (matched) {
                passed++;
            } else {
                failedIndexes.add(index);
            }
        }
        // 计算断言通过率，保留6位小数并去除末尾0
        BigDecimal ratio = BigDecimal.valueOf(passed)
                .divide(BigDecimal.valueOf(assertions.size()), 6, RoundingMode.HALF_UP).stripTrailingZeros();
        EvidenceReferenceValue task = taskEvidence(input);
        // 构造通过率指标
        StandardMetricOutput metric = new StandardMetricOutput(StandardMetricValue.number(
                "evaluation.text-assertion.pass-ratio", ratio, "ratio",
                EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EVALUATOR),
                List.of(task.referenceKey()));
        // 全部断言都通过才算整体PASSED
        return outcome(passed == assertions.size(), "TEXT_ASSERTION",
                Map.of("assertionCount", assertions.size(), "failedIndexes", failedIndexes), List.of(metric),
                List.of(task));
    }

    /**
     * document-change-validator：文档变更校验，检查变更制品元数据、目标哈希匹配，输出valid与accuracy指标
     * @param expected 合并后的预期配置JsonNode
     * @param input 证据包
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome documentChange(JsonNode expected, EvaluationEvidenceBundleVO input,
                                                          List<EvaluationDocumentChangeEvidenceVO> validations) {
        // 读取需要校验的制品类型，缺省为CHANGE_PROPOSAL
        String requiredType = text(expected, "requiredArtifactType");
        if (requiredType == null) {
            requiredType = "CHANGE_PROPOSAL";
        }
        // 可选目标提案正文 sha256
        String targetHash = text(expected, "targetContentSha256");
        String finalRequiredType = requiredType;
        // 过滤出匹配类型的变更制品
        List<EvaluationArtifactEvidenceVO> changes = input.artifacts().stream()
                .filter(artifact -> finalRequiredType.equals(artifact.artifactType())).toList();
        Set<Long> artifactIds = changes.stream().map(EvaluationArtifactEvidenceVO::id).collect(
                java.util.stream.Collectors.toSet());
        List<EvaluationDocumentChangeEvidenceVO> matched = validations.stream()
                .filter(value -> input.taskId().equals(value.taskId()) && artifactIds.contains(value.artifactId()))
                .toList();
        boolean valid = !changes.isEmpty() && matched.size() == changes.size()
                && matched.stream().allMatch(value -> value.valid() && !value.conflicted());
        // 计算准确率：结构预览合法且可选目标正文hash匹配
        BigDecimal accuracy = !valid ? BigDecimal.ZERO
                : targetHash == null ? BigDecimal.ONE
                : matched.stream().anyMatch(value -> targetHash.equals(value.proposedContentSha256()))
                ? BigDecimal.ONE : BigDecimal.ZERO;
        List<EvidenceReferenceValue> evidence = artifactEvidence(input);
        List<String> refs = evidence.stream().map(EvidenceReferenceValue::referenceKey).toList();
        // 输出valid、accuracy两个指标
        List<StandardMetricOutput> metrics = List.of(
                new StandardMetricOutput(StandardMetricValue.bool("evaluation.document-change.valid", valid,
                        EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EVALUATOR), refs),
                new StandardMetricOutput(StandardMetricValue.number("evaluation.document-change.accuracy", accuracy,
                        "ratio", EvaluationMetricDirection.HIGHER_IS_BETTER,
                        EvaluationMetricSource.EVALUATOR), refs));
        // 整体通过条件：制品合法且哈希匹配
        return outcome(valid && accuracy.compareTo(BigDecimal.ONE) == 0, "DOCUMENT_CHANGE",
                Map.of("matchingArtifactCount", matched.size(), "targetHashConfigured", targetHash != null),
                metrics, evidence);
    }

    /**
     * isolation-invariant：隔离不变量校验，要求无变更请求、无外部MCP调用
     * @param input 证据包
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome isolation(EvaluationEvidenceBundleVO input) {
        // 不变量：不存在变更请求，不存在外部MCP调用
        boolean success = input.changeRequestCount() == 0 && input.externalMcpCallCount() == 0;
        EvidenceReferenceValue task = taskEvidence(input);
        return boolOutcome(success, "ISOLATION_INVARIANT",
                "evaluation.isolation-invariant.success", task);
    }

    /**
     * audit-ledger-integrity：审计账本完整性校验，校验任务终态、执行/链路/时间/Token账本链路完整性
     * @param input 证据包
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome auditLedger(EvaluationEvidenceBundleVO input) {
        // 任务必须处于终态
        boolean terminal = Set.of("COMPLETED", "FAILED", "TERMINATED").contains(input.taskStatus());
        // 执行记录存在且状态非空
        boolean executionLinked = input.executionId() != null && input.executionStatus() != null;
        // trace链路存在
        boolean traceLinked = input.traceId() != null && !input.traceId().isBlank();
        // 起止时间合法：结束时间不能早于开始时间
        boolean timeValid = input.startedAt() != null && input.finishedAt() != null
                && !input.finishedAt().isBefore(input.startedAt());
        // token输入或输出至少存在一项
        boolean tokenLinked = input.inputTokens() != null || input.outputTokens() != null;
        // 全部条件同时满足才算审计账本完整
        boolean success = terminal && executionLinked && traceLinked && timeValid && tokenLinked;
        List<EvidenceReferenceValue> evidence = new ArrayList<>();
        evidence.add(taskEvidence(input));
        // 追加执行记录证据
        if (executionLinked) {
            evidence.add(new EvidenceReferenceValue("execution", EvaluationEvidenceType.AGENT_EXECUTION,
                    String.valueOf(input.executionId()), null, input.executionStatus(),
                    JsonUtils.toJson(Map.of("taskId", input.taskId()))));
        }
        // 追加token账本证据
        if (tokenLinked) {
            evidence.add(new EvidenceReferenceValue("token-ledger", EvaluationEvidenceType.TOKEN_LEDGER,
                    String.valueOf(input.executionId()), null, "TOKEN_LEDGER_PRESENT",
                    JsonUtils.toJson(Map.of("taskId", input.taskId()))));
        }
        return boolOutcome(success, "AUDIT_LEDGER_INTEGRITY",
                "evaluation.audit-ledger-integrity.success", evidence);
    }

    /**
     * 布尔结果快捷封装：单布尔判定，自动生成布尔指标
     * @param passed 是否通过
     * @param code 结果业务编码
     * @param metricKey 指标key
     * @param evidence 证据引用
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome boolOutcome(boolean passed, String code, String metricKey,
                                                       EvidenceReferenceValue evidence) {
        return boolOutcome(passed, code, metricKey, List.of(evidence));
    }

    /**
     * 布尔结果快捷封装重载
     * @param passed 是否通过
     * @param code 结果业务编码
     * @param metricKey 指标key
     * @param evidence 证据引用列表
     * @return 评估结果
     */
    private DeterministicEvaluationOutcome boolOutcome(boolean passed, String code, String metricKey,
                                                       List<EvidenceReferenceValue> evidence) {
        // 提取证据引用key列表用于指标关联
        List<String> refs = evidence.stream().map(EvidenceReferenceValue::referenceKey).toList();
        // 构造布尔类型指标
        StandardMetricOutput metric = new StandardMetricOutput(StandardMetricValue.bool(metricKey, passed,
                EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EVALUATOR), refs);
        return outcome(passed, code, Map.of("passed", passed), List.of(metric), evidence);
    }

    /**
     * 通用结果组装方法，构造 DeterministicEvaluationOutcome
     * @param passed 是否通过
     * @param code 业务编码
     * @param details 详情Map，序列化为detailsJson
     * @param metrics 指标列表
     * @param evidence 证据引用列表
     * @return 评估输出对象
     */
    private DeterministicEvaluationOutcome outcome(boolean passed, String code, Map<String, ?> details,
                                                   List<StandardMetricOutput> metrics,
                                                   List<EvidenceReferenceValue> evidence) {
        // 拼接PASSED/FAILED后缀，组装最终输出record
        return new DeterministicEvaluationOutcome(
                passed ? EvaluationResultStatus.PASSED : EvaluationResultStatus.FAILED,
                code + (passed ? "_PASSED" : "_FAILED"), JsonUtils.toJson(details), metrics, evidence);
    }

    /**
     * 构造任务证据引用
     * @param input 证据包
     * @return 任务EvidenceReferenceValue
     */
    private static EvidenceReferenceValue taskEvidence(EvaluationEvidenceBundleVO input) {
        return new EvidenceReferenceValue("task", EvaluationEvidenceType.TASK, String.valueOf(input.taskId()),
                null, input.taskStatus(), JsonUtils.toJson(Map.of("taskId", input.taskId())));
    }

    /**
     * 批量构造产出物Artifact证据引用
     * @param input 证据包
     * @return 产出物证据列表
     */
    private static List<EvidenceReferenceValue> artifactEvidence(EvaluationEvidenceBundleVO input) {
        return input.artifacts().stream().map(artifact -> new EvidenceReferenceValue(
                "artifact-" + artifact.id(), EvaluationEvidenceType.EXECUTION_ARTIFACT,
                String.valueOf(artifact.id()), artifact.payloadSha256(), artifact.artifactType(),
                JsonUtils.toJson(Map.of("taskId", input.taskId(), "sequenceNo", artifact.sequenceNo())))).toList();
    }

    /**
     * JSON字符串转ObjectNode，校验非空且为对象类型
     * @param json json字符串
     * @return ObjectNode
     */
    private static ObjectNode object(String json) {
        // 空json默认返回空对象
        if (json == null || json.isBlank()) {
            return (ObjectNode) JsonUtils.parse("{}", JsonNode.class);
        }
        JsonNode value = JsonUtils.parse(json, JsonNode.class);
        // 必须是JSON对象，不允许数组/基础类型
        if (value == null || !value.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evaluator expectedJson 必须是 JSON object");
        }
        return (ObjectNode) value;
    }

    /**
     * 读取JsonNode整型字段，空时返回默认值
     * @param node json节点
     * @param field 字段名
     * @param defaultValue 默认值
     * @return 解析后的int
     */
    private static int integer(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value == null ? defaultValue : value.asInt(defaultValue);
    }

    /**
     * 读取JsonNode文本字段，非文本返回null
     * @param node json节点
     * @param field 字段名
     * @return 文本值
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() ? null : value.textValue();
    }

    /**
     * 读取JSON字符串数组转为不可重复字符串集合，非法/重复抛异常
     * @param node 数组JsonNode
     * @return 字符串集合
     */
    private static Set<String> strings(JsonNode node) {
        // 非数组直接返回空集合
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> values = new HashSet<>();
        node.forEach(item -> {
            // 校验：文本类型、非空、无重复
            if (!item.isTextual() || item.textValue().isBlank() || !values.add(item.textValue())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字符串数组包含无效或重复项");
            }
        });
        return Set.copyOf(values);
    }
}
