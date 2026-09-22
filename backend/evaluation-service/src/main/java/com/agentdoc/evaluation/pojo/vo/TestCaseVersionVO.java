package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "测试用例版本信息")
public record TestCaseVersionVO(
        @Schema(description = "测试用例版本ID")
        Long id,

        @Schema(description = "所属测试用例ID")
        Long testCaseId,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "版本号")
        Integer versionNo,

        @Schema(description = "版本状态 DRAFT/LIVE")
        String status,

        @Schema(description = "来源任务ID")
        Long sourceTaskId,

        @Schema(description = "来源执行记录ID")
        Long sourceExecutionId,

        @Schema(description = "源输入Schema版本")
        Integer sourceInputSchemaVersion,

        @Schema(description = "源输入内容哈希")
        String sourceInputHash,

        @Schema(description = "源执行结果Schema版本")
        Integer sourceExecutionSchemaVersion,

        @Schema(description = "源执行结果哈希")
        String sourceExecutionHash,

        @Schema(description = "关联文档快照版本号")
        Long documentVersionSnapshot,

        @Schema(description = "文档内容SHA256摘要")
        String documentContentSha256,

        @Schema(description = "预期输出Schema版本")
        Integer expectedSchemaVersion,

        @Schema(description = "预期基准输出JSON")
        String expectedJson,

        @Schema(description = "来源采集渠道类型")
        String sourceType,

        @Schema(description = "数据脱敏、裁剪等清洗备注")
        String sanitizationNote,

        @Schema(description = "版本整体内容哈希，用于变更识别")
        String contentHash,

        @Schema(description = "发布时间，草稿版本为空")
        LocalDateTime publishedAt,

        @Schema(description = "创建人ID")
        Long createdBy
) {
    /**
     * 数据库实体转换为视图对象
     * @param value 测试用例版本实体
     * @return 对外VO
     */
    public static TestCaseVersionVO from(EvaluationTestCaseVersionEntity value) {
        return new TestCaseVersionVO(value.getId(), value.getTestCaseId(), value.getSpaceId(), value.getVersionNo(),
                value.getStatus(), value.getSourceTaskId(), value.getSourceExecutionId(),
                value.getSourceInputSchemaVersion(), value.getSourceInputHash(),
                value.getSourceExecutionSchemaVersion(), value.getSourceExecutionHash(),
                value.getDocumentVersionSnapshot(), value.getDocumentContentSha256(),
                value.getExpectedSchemaVersion(), value.getExpectedJson(), value.getSourceType(),
                value.getSanitizationNote(), value.getContentHash(), value.getPublishedAt(), value.getCreatedBy());
    }
}