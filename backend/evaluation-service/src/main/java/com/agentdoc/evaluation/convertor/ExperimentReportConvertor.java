package com.agentdoc.evaluation.convertor;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.constant.ExperimentReportConstant;
import com.agentdoc.evaluation.enums.ExperimentReportCompatibility;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportEntity;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportV1VO.ContentVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportV1VO.SelectedRecordIdsVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportVO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.agentdoc.evaluation.enums.ExperimentReportCompatibility.*;

/** 严格按 v1 字段与 JSON 类型读取；不通过 Jackson 的字符串/数值强制转换猜测格式。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentReportConvertor {
    private final RedisUtils redisUtils;

    public ExperimentReportVO toVO(ExperimentReportEntity entity) {
        ExperimentReportCompatibility code = compatibility(entity);
        SelectedRecordIdsVO selected = null;
        ContentVO content = null;
        if (code == SUPPORTED) {
            selected = JsonUtils.parseStrict(entity.getSelectedRecordIdsJson(), SelectedRecordIdsVO.class);
            content = JsonUtils.parseStrict(entity.getReportJson(), ContentVO.class);
            if (selected == null || content == null) {
                code = PAYLOAD_INVALID;
                selected = null;
                content = null;
            }
        }
        if (code == CONTENT_HASH_MISMATCH) { alert(entity); }
        return new ExperimentReportVO(entity.getExperimentId(), entity.getRevision(), entity.getReportSchemaVersion(),
                entity.getManifestHash(), entity.getCalculationInputHash(), selected, content,
                entity.getContentHash(), entity.getGeneratedBy(), entity.getCreatedAt(), code == SUPPORTED, code);
    }

    private ExperimentReportCompatibility compatibility(ExperimentReportEntity entity) {
        Integer schema = entity.getReportSchemaVersion();
        if (schema == null) { return SCHEMA_MISSING; }
        if (schema <= 0) { return SCHEMA_INVALID; }
        if (schema != ExperimentReportConstant.V1_SCHEMA) { return SCHEMA_UNSUPPORTED; }
        JsonNode selected = JsonUtils.parseStrict(entity.getSelectedRecordIdsJson(), JsonNode.class);
        JsonNode content = JsonUtils.parseStrict(entity.getReportJson(), JsonNode.class);
        if (!matches(selected, SelectedRecordIdsVO.class)
                || !matches(content, ContentVO.class)) { return PAYLOAD_INVALID; }
        return Objects.equals(entity.getContentHash(), StableSnapshotUtils.sha256Utf8(entity.getReportJson()))
                ? SUPPORTED : CONTENT_HASH_MISMATCH;
    }

    /** 仅检查本文件固定的报告 record 树，不是可扩展的 JSON schema 引擎。 */
    private boolean matches(JsonNode node, Type type) {
        if (node == null) { return false; }
        if (type instanceof ParameterizedType generic) {
            if (generic.getRawType() == List.class) {
                if (!node.isArray()) { return false; }
                for (JsonNode item : node) {
                    if (item.isNull() || !matches(item, generic.getActualTypeArguments()[0])) { return false; }
                }
                return true;
            }
            if (generic.getRawType() == Map.class) {
                if (!node.isObject()) { return false; }
                for (JsonNode value : node) {
                    if (value.isNull() || !matches(value, generic.getActualTypeArguments()[1])) { return false; }
                }
                return true;
            }
            return false;
        }
        if (!(type instanceof Class<?> target)) { return false; }
        if (target.isRecord()) {
            RecordComponent[] fields = target.getRecordComponents();
            if (!node.isObject() || node.size() != fields.length) { return false; }
            for (RecordComponent field : fields) {
                if (!matches(node.get(field.getName()), field.getGenericType())) { return false; }
            }
            return true;
        }
        if (node.isNull()) { return !target.isPrimitive(); }
        if (target == String.class) { return node.isTextual(); }
        if (target == Boolean.class) { return node.isBoolean(); }
        if (target == BigDecimal.class) { return node.isNumber(); }
        if (target == Long.class || target == long.class) { return node.isIntegralNumber() && node.canConvertToLong(); }
        if (target == int.class) { return node.isIntegralNumber() && node.canConvertToInt(); }
        return false;
    }

    private void alert(ExperimentReportEntity entity) {
        try {
            if (redisUtils.setIfAbsent(ExperimentReportConstant.INTEGRITY_ALERT_PREFIX + entity.getId(),
                    "alerted", Duration.ofSeconds(ExperimentReportConstant.INTEGRITY_ALERT_SECONDS))) {
                log.warn("报告完整性异常 experimentId={}, revision={}, reason=CONTENT_HASH_MISMATCH",
                        entity.getExperimentId(), entity.getRevision());
            }
        } catch (RuntimeException unavailable) {
            // 告警后端故障不把只读兼容降级转换为 500，也不重复输出报告原文。
            log.debug("报告完整性告警去重不可用 exceptionType={}", unavailable.getClass().getSimpleName());
        }
    }
}
