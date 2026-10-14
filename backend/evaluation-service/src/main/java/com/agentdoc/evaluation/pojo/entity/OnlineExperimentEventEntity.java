package com.agentdoc.evaluation.pojo.entity;
import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("online_experiment_event")
public class OnlineExperimentEventEntity extends BaseEntity {
    @Schema(description = "人工原因与确认载荷；不包含凭证和正文") private String detailJson;
    @Schema(description="实验") private Long experimentId;
    @Schema(description="空间") private Long spaceId;
    @Schema(description="事件") private String eventType;
    @Schema(description="HUMAN/SERVICE") private String actorType;
    @Schema(description="实际调用身份") private String actorId;
    @Schema(description="授权人") private Long authorizedBy;
    @Schema(description="状态版本") private Long stateVersion;
    @Schema(description="原因") private String reasonCode;
    @Schema(description="分配") private Long assignmentId;
}
