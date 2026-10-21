package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.HashSet;
import java.util.List;

/** 只查询展示名称，不加载配置 JSON 或冻结输入。 */
@Data
public class CaseVersionNamesSearchParam {
    @Schema(description = "当前空间ID") private Long spaceId;
    @Schema(description = "用例版本ID，去重且最多100个") private List<Long> versionIds;
    public void validate() {
        if (spaceId == null || spaceId <= 0 || versionIds == null || versionIds.isEmpty()
                || versionIds.size() > PageParam.MAX_PAGE_SIZE || versionIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(versionIds).size() != versionIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用例名称查询需要当前空间与1～100个不同版本ID");
        }
    }
}
