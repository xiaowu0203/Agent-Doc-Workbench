package com.agentdoc.agent.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.agentdoc.agent.constant.ModelConstant.DEFAULT_PAGE_SIZE;
import static com.agentdoc.agent.constant.ModelConstant.MAX_SEARCH_KEYWORD_LENGTH;

/**
 * 平台模型分页查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "平台模型分页查询参数")
public class ModelSearchParam extends PageParam {

    /** 模型展示名称或模型标识关键字。 */
    @Size(max = MAX_SEARCH_KEYWORD_LENGTH)
    @Schema(description = "模型展示名称或模型标识关键字")
    private String keyword;

    /** 模型供应商编码。 */
    @Schema(description = "模型供应商编码")
    private String provider;

    /** 状态：0 禁用，1 启用。 */
    @Min(0)
    @Max(1)
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;

    /** 模型适配器编码。 */
    @Schema(description = "模型适配器编码")
    private String adapterType;

    public ModelSearchParam() {
        setPageSize(DEFAULT_PAGE_SIZE);
    }
}
