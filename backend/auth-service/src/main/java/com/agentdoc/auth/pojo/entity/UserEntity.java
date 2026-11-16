package com.agentdoc.auth.pojo.entity;

import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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

    @Schema(description = "密码哈希（BCrypt）；外部身份账号可为空")
    private String passwordHash;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "所属部门 ID")
    private Long departmentId;

    @Schema(description = "职位")
    private String jobTitle;

    @Schema(description = "账号状态：0 禁用 / 1 启用")
    private Integer status;

    @Schema(description = "用户级人类认证版本；不适用于服务或任务凭据")
    // 版本由撤销入口原子递增，普通资料或登录时间更新不得写回旧版本。
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long authVersion = 0L;

    @Schema(description = "OIDC认证拒绝边界（UTC epoch秒）；auth_time须严格大于此值")
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long oidcAuthNotBefore = 0L;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginAt;

    /**
     * 实体转 VO，剥离密码等敏感字段对外输出。
     * @return 用户信息 VO
     */
    public UserVO toVO() {
        return new UserVO(getId(), username, nickname, email, avatarUrl);
    }
}
