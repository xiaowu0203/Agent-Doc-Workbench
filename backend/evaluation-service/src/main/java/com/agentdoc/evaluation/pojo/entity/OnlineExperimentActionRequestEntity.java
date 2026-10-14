package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_experiment_action_request")
public class OnlineExperimentActionRequestEntity extends BaseEntity {
    @Schema(description = "实验") private Long experimentId;
    @Schema(description = "当前人类操作者") private Long actorId;
    @Schema(description = "服务端确定动作") private String action;
    @Schema(description = "动作请求键") private String requestKey;
    @Schema(description = "原请求摘要") private String requestHash;
    @Schema(description = "已提交动作的不可变结果") private String resultJson;
    @Schema(description = "非秘密人工请求与原因") private String requestJson;
}
