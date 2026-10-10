package com.agentdoc.evaluation.convertor;

import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentSummaryVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** 已经权限和 hash 验证的清单只投影参数，不返回 Prompt/期望正文。 */
@Component
public class OnlineExperimentConvertor {
    public OnlineExperimentVO detail(OnlineExperimentEntity entity, JsonNode payload, Long participating) {
        return new OnlineExperimentVO(summary(entity, payload, participating),
                payload == null ? null : JsonUtils.parseStrict(JsonUtils.toJson(payload.get("baseline")), AgentOnlineConfigPairVO.TemplateIdentity.class),
                payload == null ? null : JsonUtils.parseStrict(JsonUtils.toJson(payload.get("candidate")), AgentOnlineConfigPairVO.TemplateIdentity.class),
                payload == null ? null : payload.path("dependencyHash").asText(),
                payload == null ? null : payload.get("analysisPlan"),
                payload == null ? null : payload.get("budgetPlan"),
                payload == null ? null : payload.get("windowPlan"),
                payload == null ? null : payload.get("protectionPlan"),
                entity.getActiveSlot(), entity.getStartedAt(), entity.getAssignmentDeadline(),
                entity.getObservationDeadline(), entity.getDecision());
    }

    public OnlineExperimentSummaryVO summary(OnlineExperimentEntity entity, JsonNode payload, Long participating) {
        var reasons = new ArrayList<String>();
        if (payload == null) { reasons.add(OnlineReasonCode.UNSUPPORTED_ONLINE_SCHEMA.name()); }
        if (entity.getReasonCode() != null) { reasons.add(entity.getReasonCode()); }
        return new OnlineExperimentSummaryVO(entity.getId().toString(), entity.getSpaceId().toString(), entity.getAgentId().toString(),
                entity.getName(), entity.getStatus(), entity.getStateVersion(), entity.getManifestSchemaVersion(), entity.getManifestHash(),
                payload == null ? null : payload.path("bucketProtocol").path("weight").asInt(),
                payload == null ? null : payload.path("analysisPlan").path("mode").asText(),
                payload == null ? null : payload.path("documentIds").size(), payload == null ? null : participating, entity.getAssignedTaskCount(),
                text(entity.getAuthorizedTokenBudget()), text(entity.getReservedTokenBudget()), text(entity.getConsumedTokens()),
                entity.getBaselineSlotCount(), entity.getCandidateSlotCount(), entity.getUnknownTaskCount(), List.copyOf(reasons),
                entity.getLastReconciledAt(), entity.getCreatedBy().toString(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private static String text(Number value) { return value == null ? null : value.toString(); }
}
