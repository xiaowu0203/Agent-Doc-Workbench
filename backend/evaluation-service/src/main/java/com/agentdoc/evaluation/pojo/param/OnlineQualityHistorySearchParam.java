package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "隐藏分组的当前原始质量反馈链分页查询")
public class OnlineQualityHistorySearchParam extends PageParam {
    @Schema(description = "原始目标TEXT或CHANGE_SET，须与已冻结证据一致") private String target;
    @Override public void validate() {
        super.validate();
        if (target == null || !Set.of("TEXT", "CHANGE_SET").contains(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原始评审目标无效");
        }
    }
}
