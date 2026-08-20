package com.agentdoc.document.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间成员实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member")
public class MemberEntity extends BaseLogicDeleteEntity {

    private Long spaceId;

    private Long userId;

    /** 角色：1 所有者 / 2 编辑者 / 3 观察者 */
    private Integer role;
}
