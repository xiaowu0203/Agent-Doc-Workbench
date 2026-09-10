package com.agentdoc.auth.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_SEARCH_KEYWORD_LENGTH;

/**
 * 平台用户分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "平台用户分页查询条件")
public class PlatformUserSearchParam extends PageParam {

    @Size(max = MAX_SEARCH_KEYWORD_LENGTH, message = "搜索关键字过长")
    @Schema(description = "用户名、昵称或邮箱关键字")
    private String keyword;

    @Min(value = 0, message = "用户状态无效")
    @Max(value = 1, message = "用户状态无效")
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;

    @Schema(description = "所属部门 ID；0 表示未分配部门")
    private Long departmentId;

    @Schema(description = "平台角色稳定标识")
    private String platformRoleKey;
}
