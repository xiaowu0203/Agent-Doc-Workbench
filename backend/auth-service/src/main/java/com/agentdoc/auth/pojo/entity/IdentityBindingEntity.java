package com.agentdoc.auth.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 外部身份绑定保留撤销历史，不使用逻辑删除。 */
@Data
@TableName("identity_binding")
@Schema(description = "外部身份绑定")
public class IdentityBindingEntity {
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "绑定ID") private Long id;
    @Schema(description = "身份摘要") private byte[] identityKey;
    @Schema(description = "可信发行者") private String issuer;
    @Schema(description = "外部主体") private String subject;
    @Schema(description = "业务用户ID") private Long userId;
    @Schema(description = "绑定状态") private String status;
    @Schema(description = "绑定操作者") private Long boundBy;
    @Schema(description = "绑定时间") private LocalDateTime boundAt;
    @Schema(description = "撤销操作者") private Long revokedBy;
    @Schema(description = "撤销时间") private LocalDateTime revokedAt;
}
