package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_preflight_proof")
public class OnlinePreflightProofEntity extends BaseEntity {
    @Schema(description = "实验") private Long experimentId;
    @Schema(description = "预检实际操作者") private Long actorId;
    @Schema(description = "原清单") private String manifestHash;
    @Schema(description = "当时的依赖，缺失保留空") private String dependencyHash;
    @Schema(description = "当时状态版本") private Long stateVersion;
    @Schema(description = "预检摘要") private String proofHash;
    @Schema(description = "UTC 失效时点") private LocalDateTime expiresAt;
}
