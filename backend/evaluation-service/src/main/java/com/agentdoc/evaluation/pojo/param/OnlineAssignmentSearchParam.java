package com.agentdoc.evaluation.pojo.param;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实验内分配分页条件")
public class OnlineAssignmentSearchParam extends PageParam {
    @Schema(description = "BASELINE/CANDIDATE") private String variant;
    @Schema(description = "文档身份，可选") private String documentId;
    @Schema(description = "任务身份，可选") private String taskId;
    @Schema(description = "确认状态，可选") private String confirmationStatus;
    @Schema(description = "结算状态，可选") private String settlementStatus;
    @Override
    public void validate() {
        super.validate();
        try {
            if (documentId != null) { OnlineProtocolUtils.id(documentId); }
            if (taskId != null) { OnlineProtocolUtils.id(taskId); }
        } catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, ID_INVALID.name()); }
        if (variant != null && !Set.of("BASELINE", "CANDIDATE").contains(variant)
                || confirmationStatus != null && !Set.of("UNCONFIRMED", "CONFIRMED", "UNKNOWN").contains(confirmationStatus)
                || settlementStatus != null && !Set.of("RESERVED", "SETTLED", "RELEASED", "UNKNOWN").contains(settlementStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name());
        }
    }
}
