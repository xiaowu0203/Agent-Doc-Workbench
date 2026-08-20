package com.agentdoc.auth.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user`")
public class UserEntity extends BaseLogicDeleteEntity {

    private String username;

    private String passwordHash;

    private String nickname;

    private String email;

    private String avatarUrl;

    private Integer status;
}
