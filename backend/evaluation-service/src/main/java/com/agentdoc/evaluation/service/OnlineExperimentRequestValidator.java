package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineRuleRole;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentCreateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineAnalysisPlanDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineRuleBindingDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineRuleExpectedDTO;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.nio.charset.StandardCharsets;

import static com.agentdoc.evaluation.constant.OnlineExperimentConstant.*;

/** 创建载荷先严格 JSON 校验，再按领域顺序规范化；不截断用户输入。 */
public final class OnlineExperimentRequestValidator {
    private OnlineExperimentRequestValidator() { }

    public static OnlineExperimentCreateDTO parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
            throw invalid(MANIFEST_TOO_LARGE.name());
        }
        JsonNode node;
        try { node = OnlineProtocolUtils.object(json); }
        catch (IllegalArgumentException invalid) { throw invalid(MANIFEST_INVALID.name()); }
        for (String field : List.of("spaceId", "agentId", "authorizedTokenBudget", "perTaskTokenLimit")) {
            requireText(node.get(field));
        }
        if (!node.path("documentIds").isArray() || !node.path("rules").isArray()) { throw invalid(MANIFEST_INVALID.name()); }
        node.path("documentIds").forEach(OnlineExperimentRequestValidator::requireText);
        node.path("rules").forEach(rule -> {
            requireText(rule.get("evaluatorVersionId"));
            rule.path("expectedBindings").forEach(expected -> requireText(expected.get("documentId")));
        });
        OnlineExperimentCreateDTO dto = JsonUtils.parseStrict(json, OnlineExperimentCreateDTO.class);
        return normalize(dto);
    }

    public static OnlineExperimentCreateDTO normalize(OnlineExperimentCreateDTO dto) {
        if (dto == null) { throw invalid(MANIFEST_INVALID.name()); }
        key(dto.clientRequestKey());
        id(dto.spaceId());
        id(dto.agentId());
        if (dto.name() == null || dto.name().trim().isEmpty() || codePoints(dto.name().trim()) > 100
                || dto.candidateAgentPrompt() == null || dto.candidateAgentPrompt().isBlank()) { throw invalid(MANIFEST_INVALID.name()); }
        long total = id(dto.authorizedTokenBudget());
        long perTask = id(dto.perTaskTokenLimit());
        if (total < perTask || dto.maxTaskCount() == null || dto.maxTaskCount() < 1
                || dto.maxTaskCount() > MAX_TASKS || dto.candidateWeightBps() == null
                || dto.candidateWeightBps() < 1 || dto.candidateWeightBps() >= 10000) { throw invalid(MANIFEST_INVALID.name()); }
        if (dto.documentIds() == null || dto.documentIds().isEmpty() || dto.documentIds().size() > MAX_DOCUMENTS
                || new HashSet<>(dto.documentIds()).size() != dto.documentIds().size()) { throw invalid(SCOPE_INVALID.name()); }
        List<String> documents = dto.documentIds().stream().sorted(Comparator.comparingLong(OnlineExperimentRequestValidator::id)).toList();
        int window = dto.assignmentWindowSeconds() == null ? DEFAULT_ASSIGNMENT_SECONDS : dto.assignmentWindowSeconds();
        int observation = dto.completionObservationSeconds() == null ? DEFAULT_OBSERVATION_SECONDS : dto.completionObservationSeconds();
        if (window < 1 || window > MAX_ASSIGNMENT_SECONDS || observation < 0 || observation > MAX_OBSERVATION_SECONDS) {
            throw invalid(MANIFEST_INVALID.name());
        }
        var plan = dto.analysisPlan();
        if (plan == null || plan.mode() == null || plan.humanQualityRequired() == null
                || plan.mdeReason() == null || plan.mdeReason().isBlank() || codePoints(plan.mdeReason()) > 2000) { throw invalid(MANIFEST_INVALID.name()); }
        String mde = decimal(plan.mdeAbsoluteRatio());
        if (new BigDecimal(mde).signum() <= 0 || new BigDecimal(mde).compareTo(BigDecimal.ONE) > 0) { throw invalid(MANIFEST_INVALID.name()); }
        if (dto.rules() == null || dto.rules().isEmpty() || dto.rules().size() > 50) { throw invalid(MANIFEST_INVALID.name()); }
        var keys = new HashSet<String>();
        List<OnlineRuleBindingDTO> rules = dto.rules().stream().map(rule -> {
            if (rule == null) { throw invalid(MANIFEST_INVALID.name()); }
            key(rule.ruleKey());
            id(rule.evaluatorVersionId());
            if (!keys.add(rule.ruleKey()) || rule.role() == null || rule.evidenceTarget() == null
                    || rule.valueType() == null || rule.comparison() == null || rule.threshold() == null
                    || rule.metricKey() == null || rule.unit() == null) { throw invalid(MANIFEST_INVALID.name()); }
            if (rule.expectedBindings() == null || rule.expectedBindings().size() != documents.size()) {
                throw invalid(EXPECTED_MAPPING_MISSING.name());
            }
            var expectedIds = new HashSet<String>();
            var expected = rule.expectedBindings().stream().map(binding -> {
                if (binding == null || !documents.contains(binding.documentId()) || !expectedIds.add(binding.documentId())
                        || binding.expectedJson() == null || !binding.expectedJson().isObject()) { throw invalid(EXPECTED_MAPPING_MISSING.name()); }
                return new OnlineRuleExpectedDTO(binding.documentId(), OnlineProtocolUtils.object(
                        OnlineProtocolUtils.canonicalNode(binding.expectedJson())));
            }).sorted(Comparator.comparingLong(value -> id(value.documentId()))).toList();
            JsonNode threshold = rule.threshold();
            if (rule.valueType() == EvaluationMetricValueType.NUMBER) {
                if (!threshold.isTextual()) { throw invalid(MANIFEST_INVALID.name()); }
                threshold = JsonUtils.parseStrict(JsonUtils.toJson(decimal(threshold.textValue())), JsonNode.class);
            } else if (rule.valueType() == EvaluationMetricValueType.BOOLEAN && !threshold.isBoolean()
                    || rule.valueType() == EvaluationMetricValueType.STRING && !threshold.isTextual()) { throw invalid(MANIFEST_INVALID.name()); }
            return new OnlineRuleBindingDTO(rule.ruleKey(), rule.role(), rule.evaluatorVersionId(), rule.metricKey(),
                    rule.valueType(), rule.unit(), rule.comparison(), threshold, rule.evidenceTarget(), expected);
        }).sorted(Comparator.comparing(OnlineRuleBindingDTO::ruleKey)).toList();
        boolean primaryQuality = rules.stream().anyMatch(rule -> rule.role() == OnlineRuleRole.PRIMARY
                && (rule.evidenceTarget().name().equals("ORIGINAL_TEXT") || rule.evidenceTarget().name().equals("ORIGINAL_CHANGE")));
        if (!primaryQuality) { throw invalid(PRIMARY_QUALITY_REQUIRED.name()); }
        var normalized = new OnlineExperimentCreateDTO(dto.clientRequestKey(), dto.spaceId(), dto.agentId(), dto.name().trim(),
                documents, dto.candidateAgentPrompt(), dto.candidateWeightBps(), dto.authorizedTokenBudget(), dto.maxTaskCount(),
                dto.perTaskTokenLimit(), window, observation, new OnlineAnalysisPlanDTO(plan.mode(), mde, plan.mdeReason().trim(),
                plan.humanQualityRequired()), rules);
        // 同时拒绝非法 Unicode 与任何未声明浮点。
        OnlineProtocolUtils.canonical("online.create-request", normalized);
        return normalized;
    }

    public static long id(String value) {
        try { return OnlineProtocolUtils.id(value); }
        catch (IllegalArgumentException invalid) { throw invalid(ID_INVALID.name()); }
    }

    public static void key(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,64}")) { throw invalid(REQUEST_KEY_REQUIRED.name()); }
    }

    private static void requireText(JsonNode value) {
        if (value == null || !value.isTextual()) { throw invalid(ID_INVALID.name()); }
    }

    private static String decimal(String value) {
        if (value == null || value.length() > 64 || !value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) { throw invalid(MANIFEST_INVALID.name()); }
        var number = new BigDecimal(value).stripTrailingZeros();
        return number.signum() == 0 ? "0" : number.toPlainString();
    }

    private static int codePoints(String value) { return value.codePointCount(0, value.length()); }
    private static BusinessException invalid(String code) { return new BusinessException(ErrorCode.BAD_REQUEST, code); }
}
