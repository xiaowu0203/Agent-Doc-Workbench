package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建测试用例草稿版本DTO
 * <p>
 * 基于已完成的任务快照生成测试用例草稿；固化基线快照、预期输出与来源信息。
 * 草稿版本可继续绑定评估器，发布后基线与绑定关系冻结，用于评估回放。
 *
 * @param testCaseId           所属测试用例主记录ID
 * @param sourceTaskId        来源工作台任务ID，从中提取基线快照
 * @param expectedSchemaVersion 预期输出Schema版本号，用于解析校验
 * @param expectedJson        预期结果JSON，作为回放评估的基准参考
 * @param sourceType          来源类型标识，最大32字符，区分不同采集渠道
 * @param sanitizationNote    数据清洗备注，选填，记录脱敏/裁剪说明，最大1000字符
 */
@Schema(description = "创建测试用例草稿版本")
public record TestCaseVersionCreateDTO(
        @NotNull(message = "testCaseId 不能为空")
        @Schema(description = "所属测试用例ID")
        Long testCaseId,

        @NotNull(message = "sourceTaskId 不能为空")
        @Schema(description = "来源任务ID，用于加载基线快照")
        Long sourceTaskId,

        @NotNull(message = "expectedSchemaVersion 不能为空")
        @Schema(description = "预期输出Schema版本")
        Integer expectedSchemaVersion,

        @NotBlank(message = "expectedJson 不能为空")
        @Schema(description = "预期结果JSON基准")
        String expectedJson,

        @NotBlank(message = "sourceType 不能为空")
        @Size(max = 32, message = "sourceType 不能超过32字符")
        @Schema(description = "来源类型标识")
        String sourceType,

        @Size(max = 1000, message = "数据清洗备注不能超过1000字符")
        @Schema(description = "数据脱敏、裁剪等清洗说明，选填")
        String sanitizationNote
) {}