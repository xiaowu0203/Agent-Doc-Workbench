package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.pojo.vo.MemberVO;
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

    @Schema(description = "角色：1 所有者 / 2 编辑者 / 3 观察者")
    private Integer role;

    /**
     * 转换为视图对象。
     * @return 成员视图对象
     */
    public MemberVO toVO() {
        return new MemberVO(getId(), userId, SpaceRole.fromCode(role), getCreatedAt());
    }
}
