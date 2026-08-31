package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.agentdoc.document.pojo.vo.MemberVO;
import com.agentdoc.document.pojo.vo.SpaceRoleSummaryVO;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间成员实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member")
@Schema(description = "空间成员实体")
public class MemberEntity extends BaseLogicDeleteEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "空间角色 ID")
    private Long roleId;

    /**
     * 转换为视图对象。
     * @return 成员视图对象
     */
    public MemberVO toVO(SpaceRoleSummaryVO role) {
        return new MemberVO(getId(), userId, role, getCreatedAt());
    }

    /**
     * 创建空间所有者成员关系。
     */
    public static MemberEntity owner(Long spaceId, Long userId, Long roleId) {
        MemberEntity entity = new MemberEntity();
        entity.setSpaceId(spaceId);
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        return entity;
    }
}
