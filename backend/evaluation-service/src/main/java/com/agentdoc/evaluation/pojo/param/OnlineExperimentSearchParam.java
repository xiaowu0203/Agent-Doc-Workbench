package com.agentdoc.evaluation.pojo.param;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.OnlineExperimentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "线上实验分页条件，长 ID 使用字符串")
public class OnlineExperimentSearchParam extends PageParam {
    @Schema(description = "空间身份，必需") private String spaceId;
    @Schema(description = "Agent 身份，可选") private String agentId;
    @Schema(description = "状态，可选") private OnlineExperimentStatus status;
    @Schema(description = "名称关键字，最多100码点") private String keyword;
    @Override
    public void validate() {
        super.validate();
        try {
            OnlineProtocolUtils.id(spaceId);
            if (agentId != null) { OnlineProtocolUtils.id(agentId); }
        } catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, ID_INVALID.name()); }
        if (keyword != null && keyword.codePointCount(0, keyword.length()) > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name());
        }
    }
}
