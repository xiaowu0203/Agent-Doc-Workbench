package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluator_version")
@Schema(description = "评估器版本")
public class EvaluatorVersionEntity extends BaseEntity {
    @Schema(description = "评估器 ID") private Long evaluatorId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "版本号") private Integer versionNo;
    @Schema(description = "状态") private String status;
    @Schema(description = "内置规则 key") private String evaluatorKey;
    @Schema(description = "配置 schema") private Integer configSchemaVersion;
    @Schema(description = "配置 JSON") private String configJson;
    @Schema(description = "结果 schema") private Integer resultSchemaVersion;
    @Schema(description = "实现版本") private String implementationVersion;
    @Schema(description = "内容 hash") private String contentHash;
    @Schema(description = "发布时间") private LocalDateTime publishedAt;
    @Schema(description = "创建人") private Long createdBy;
}
