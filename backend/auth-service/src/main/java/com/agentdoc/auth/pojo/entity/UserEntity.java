package com.agentdoc.auth.pojo.entity;

import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user`")
@Schema(description = "用户实体")
public class UserEntity extends BaseLogicDeleteEntity {

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码哈希（BCrypt）")
    private String passwordHash;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "账号状态：0 禁用 / 1 启用")
    private Integer status;

    /**
     * 实体转 VO，剥离密码等敏感字段对外输出。
     * @return 用户信息 VO
     */
    public UserVO toVO() {
        return new UserVO(getId(), username, nickname, email, avatarUrl);
    }
}
