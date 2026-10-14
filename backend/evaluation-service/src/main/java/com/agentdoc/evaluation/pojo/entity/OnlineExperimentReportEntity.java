package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_experiment_report")
public class OnlineExperimentReportEntity extends BaseEntity {
    @TableField(exist = false)
    @Schema(description = "仅内存使用的完整逻辑输入；大型输入读取后逐块校验") private String resolvedInputJson;
    @Schema(description = "实验") private Long experimentId;
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "追加版本") private Integer revision;
    @Schema(description = "ONLINE_V1") private String reportType;
    @Schema(description = "报告schema") private Integer schemaVersion;
    @Schema(description = "正文摘要") private String reportHash;
    @Schema(description = "计算输入摘要") private String inputHash;
    @Schema(description = "冻结的脱敏事实与选择") private String inputJson;
    @Schema(description = "不可变报告正文") private String reportJson;
    @Schema(description = "观察截止时间") private LocalDateTime observedAt;
    @Schema(description = "当前人类生成者") private Long createdBy;
}
