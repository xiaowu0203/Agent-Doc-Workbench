package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.agentdoc.common.feign.vo.SpaceBudgetVO;
import com.agentdoc.document.enums.SpaceStatus;
import com.agentdoc.document.pojo.vo.SpaceVO;
import com.agentdoc.document.pojo.vo.SpaceRoleSummaryVO;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("space")
@Schema(description = "空间实体")
public class SpaceEntity extends BaseLogicDeleteEntity {

    @Schema(description = "空间名称")
    private String name;

    @Schema(description = "空间描述")
    private String description;

    @Schema(description = "所有者用户 ID")
    private Long ownerId;

    @Schema(description = "Token 预算上限")
    private Long tokenBudget;

    @Schema(description = "状态：0 禁用 / 1 正常")
    private Integer status;

    /**
     * 转换为视图对象。
     * @param role 当前登录用户在该空间的角色
     * @param platformSuperAdmin 当前用户是否为平台超级管理员
     * @return 空间视图对象
     */
    public SpaceVO toVO(SpaceRoleSummaryVO role, boolean platformSuperAdmin) {
        return new SpaceVO(getId(), name, description, ownerId, tokenBudget,
                SpaceStatus.fromCode(status), role, platformSuperAdmin, getCreatedAt());
    }

    /**
     * 转换为空间执行预算投影。
     */
    public SpaceBudgetVO toBudgetVO() {
        return new SpaceBudgetVO(getId(), tokenBudget);
    }
}
