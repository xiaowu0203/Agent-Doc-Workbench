package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("experiment_report_request")
@Schema(description = "Experiment 报告重算请求幂等映射")
public class ExperimentReportRequestEntity extends BaseEntity {
    @Schema(description = "Experiment ID") private Long experimentId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "客户端重算幂等键") private String requestKey;
    @Schema(description = "重算请求 hash") private String requestHash;
    @Schema(description = "不可变报告 ID") private Long reportId;
    @Schema(description = "请求人") private Long createdBy;
}
