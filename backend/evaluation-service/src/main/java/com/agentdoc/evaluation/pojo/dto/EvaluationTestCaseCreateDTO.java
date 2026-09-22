package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建测试用例主记录DTO
 * <p>
 * 新建测试用例主体，仅保存基础元信息；创建完成后需再创建草稿版本，
 * 绑定基线任务快照与评估器，发布后才可被数据集引用用于评估运行。
 *
 * @param spaceId     归属空间ID
 * @param name        测试用例名称，非空，最大200字符
 * @param description 测试用例描述，选填，最大1000字符
 */
@Schema(description = "创建测试用例")
public record EvaluationTestCaseCreateDTO(
        @NotNull(message = "spaceId 不能为空")
        @Schema(description = "所属空间ID")
        Long spaceId,

        @NotBlank(message = "测试用例名称不能为空")
        @Size(max = 200, message = "测试用例名称不能超过200字符")
        @Schema(description = "测试用例名称")
        String name,

        @Size(max = 1000, message = "测试用例描述不能超过1000字符")
        @Schema(description = "测试用例描述，选填")
        String description
) {}