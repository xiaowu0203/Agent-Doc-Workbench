package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_dataset_version")
@Schema(description = "数据集版本")
public class EvaluationDatasetVersionEntity extends BaseEntity {
    @Schema(description = "数据集 ID") private Long datasetId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "版本号") private Integer versionNo;
    @Schema(description = "状态") private String status;
    @Schema(description = "内容哈希") private String contentHash;
    @Schema(description = "发布时间") private LocalDateTime publishedAt;
    @Schema(description = "创建人") private Long createdBy;
}
