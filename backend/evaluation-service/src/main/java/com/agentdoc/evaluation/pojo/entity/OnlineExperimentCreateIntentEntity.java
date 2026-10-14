package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_experiment_create_intent")
@Schema(description = "OnlineExperimentCreateIntentEntity")
public class OnlineExperimentCreateIntentEntity extends BaseEntity {
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "创建者") private Long createdBy;
    @Schema(description = "幂等键") private String requestKey;
    @Schema(description = "请求摘要") private String requestHash;
    @Schema(description = "创建状态") private String status;
}
