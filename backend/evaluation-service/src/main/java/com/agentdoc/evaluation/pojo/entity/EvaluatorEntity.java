package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluator")
@Schema(description = "空间评估器")
public class EvaluatorEntity extends BaseLogicDeleteEntity {
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "名称") private String name;
    @Schema(description = "内置规则 key") private String evaluatorKey;
    @Schema(description = "说明") private String description;
    @Schema(description = "是否归档") private Boolean archived;
    @Schema(description = "创建人") private Long createdBy;
}
