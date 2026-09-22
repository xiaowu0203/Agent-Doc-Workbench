package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_test_case_version")
@Schema(description = "测试用例版本")
public class EvaluationTestCaseVersionEntity extends BaseEntity {
    @Schema(description = "测试用例 ID") private Long testCaseId;
    @Schema(description = "所属空间 ID") private Long spaceId;
    @Schema(description = "版本号") private Integer versionNo;
    @Schema(description = "状态") private String status;
    @Schema(description = "来源 Task ID") private Long sourceTaskId;
    @Schema(description = "来源 AgentExecution ID") private Long sourceExecutionId;
    @Schema(description = "来源输入 schema") private Integer sourceInputSchemaVersion;
    @Schema(description = "来源输入 hash") private String sourceInputHash;
    @Schema(description = "来源执行 schema") private Integer sourceExecutionSchemaVersion;
    @Schema(description = "来源执行 hash") private String sourceExecutionHash;
    @Schema(description = "冻结文档版本") private Long documentVersionSnapshot;
    @Schema(description = "冻结文档内容 hash") private String documentContentSha256;
    @Schema(description = "期望 schema") private Integer expectedSchemaVersion;
    @Schema(description = "期望 JSON") private String expectedJson;
    @Schema(description = "数据来源类型") private String sourceType;
    @Schema(description = "脱敏说明") private String sanitizationNote;
    @Schema(description = "内容 hash") private String contentHash;
    @Schema(description = "发布时间") private LocalDateTime publishedAt;
    @Schema(description = "创建人") private Long createdBy;
}
