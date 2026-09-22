package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_worker_capability_segment")
@Schema(description = "Evaluation Worker 窄权限凭证段")
public class EvaluationWorkerCapabilitySegmentEntity extends BaseEntity {
    @Schema(description = "EvaluationRun ID") private Long runId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "批次号") private Integer batchNo;
    @Schema(description = "排序后 Task ID 集合哈希") private String taskSetHash;
    @Schema(description = "AES-GCM 加密能力令牌") private String encryptedCapability;
    @Schema(description = "密钥版本") private String keyVersion;
    @Schema(description = "状态") private String status;
    @Schema(description = "过期时间") private LocalDateTime expiresAt;
}
