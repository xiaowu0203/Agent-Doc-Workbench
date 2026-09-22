package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotNull;

/**
 * 数据集版本绑定用例条目DTO
 * <p>
 * 用于批量绑定/替换数据集下的测试用例版本，支持顺序排序与启用开关；
 * 仅引用已发布的 TestCaseVersion，草稿版本不可绑定至数据集。
 *
 * @param testCaseVersionId 测试用例版本主键ID
 * @param sortOrder         展示与执行顺序，数值越小越靠前
 * @param enabled           是否启用该用例；false=本次数据集运行时跳过此用例，但保留绑定关系
 */
public record DatasetCaseBindingDTO(
        @NotNull(message = "testCaseVersionId 不能为空")
        Long testCaseVersionId,

        @NotNull(message = "sortOrder 不能为空")
        Integer sortOrder,

        Boolean enabled
) {}