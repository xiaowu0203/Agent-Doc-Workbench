package com.agentdoc.evaluation.pojo.dto;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import static com.agentdoc.common.enums.OnlineReasonCode.ONLINE_RULE_INVALID;

@Schema(description = "追加线上冻结规则评价，不接受临时期望或执行配置")
public record OnlineEvaluationCreateDTO(
        @Schema(description = "assignment 和发布版本范围内的请求键") String clientRequestKey,
        @Schema(description = "实验冻结规则键") String ruleKey) {
    public static OnlineEvaluationCreateDTO parse(String json) {
        try {
            var value = OnlineProtocolUtils.object(json);
            value.fieldNames().forEachRemaining(name -> { if (!Set.of("clientRequestKey", "ruleKey").contains(name)) { throw new IllegalArgumentException(); } });
            if (!value.path("clientRequestKey").isTextual() || !value.path("ruleKey").isTextual()) { throw new IllegalArgumentException(); }
            String key = value.path("clientRequestKey").textValue(), rule = value.path("ruleKey").textValue();
            if (!key.matches("[A-Za-z0-9._:-]{1,64}") || !rule.matches("[A-Za-z0-9._:-]{1,64}")) { throw new IllegalArgumentException(); }
            return new OnlineEvaluationCreateDTO(key, rule);
        } catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, ONLINE_RULE_INVALID.name()); }
    }
}
