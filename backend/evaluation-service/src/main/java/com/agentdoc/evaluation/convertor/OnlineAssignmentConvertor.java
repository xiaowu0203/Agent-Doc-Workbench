package com.agentdoc.evaluation.convertor;

import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineAssignmentVO;
import org.springframework.stereotype.Component;

/** 只投影已存身份；权威执行读取尚未接入时明确 UNKNOWN。 */
@Component
public class OnlineAssignmentConvertor {
    public OnlineAssignmentVO toVO(OnlineAssignmentEntity value) {
        return new OnlineAssignmentVO(text(value.getId()),
                text(value.getExperimentId()),
                text(value.getSpaceId()),
                text(value.getAgentId()),
                text(value.getTaskId()),
                text(value.getDocumentId()),
                text(value.getCreatedBy()),
                value.getInputSnapshotSchemaVersion(),
                value.getInputSnapshotHash(),
                value.getBucket(),
                value.getVariant(),
                text(value.getTemplateId()),
                value.getConfigSchemaVersion(),
                value.getConfigHash(),
                value.getDependencyHash(),
                value.getBindingSchemaVersion(),
                value.getBindingHash(),
                value.getAcceptedSequence(),
                text(value.getReservedTokenBudget()),
                value.getTaskConfirmationStatus(),
                value.getDispatchStatus(),
                value.getSlotStatus(),
                value.getCancelStatus(),
                value.getSettlementStatus(),
                text(value.getExecutionId()),
                text(value.getConsumedTokens()),
                value.getReasonCode(),
                value.getTaskConfirmedAt(),
                value.getSettledAt(),
                value.getUpdatedAt(),
                value.getCreatedAt(), "UNKNOWN");
    }
    private static String text(Long value) { return value == null ? null : value.toString(); }
}
