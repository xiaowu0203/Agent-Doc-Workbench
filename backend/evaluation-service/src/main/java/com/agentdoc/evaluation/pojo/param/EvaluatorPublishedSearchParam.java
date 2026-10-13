package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.HashSet;
import java.util.List;

@Data
@Schema(description = "限定空间的已发布评估器版本批量查询")
public class EvaluatorPublishedSearchParam {
    private static final int MAX_VERSION_IDS = 50;
    @Schema(description = "空间ID，规范十进制文本") private String spaceId;
    @Schema(description = "1～50个唯一版本ID，规范十进制文本") private List<String> versionIds;
    public void validate() {
        try {
            OnlineProtocolUtils.id(spaceId);
            if (versionIds == null || versionIds.isEmpty() || versionIds.size() > MAX_VERSION_IDS
                    || new HashSet<>(versionIds).size() != versionIds.size()) { throw new IllegalArgumentException(); }
            versionIds.forEach(OnlineProtocolUtils::id);
        } catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, "发布版本查询身份或数量无效"); }
    }
}
