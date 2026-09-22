package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 数据集版本视图对象
 *
 * @param id          数据集版本主键ID
 * @param datasetId   所属数据集主记录ID
 * @param spaceId     归属空间ID
 * @param versionNo   版本号，自增
 * @param status      版本状态：DRAFT草稿 / LIVE已发布
 * @param contentHash 版本内容哈希，用于快速识别绑定用例集合是否变更
 * @param publishedAt 发布时间，草稿版本为null
 * @param createdBy   创建人ID
 */
@Schema(description = "数据集版本信息")
public record DatasetVersionVO(
        @Schema(description = "数据集版本ID")
        Long id,

        @Schema(description = "所属数据集ID")
        Long datasetId,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "版本号")
        Integer versionNo,

        @Schema(description = "版本状态 DRAFT/LIVE")
        String status,

        @Schema(description = "内容哈希，标识当前版本绑定用例快照")
        String contentHash,

        @Schema(description = "发布时间，草稿版本为空")
        LocalDateTime publishedAt,

        @Schema(description = "创建人ID")
        Long createdBy
) {
    /**
     * 从数据库实体转换为视图对象
     * @param value 数据集版本实体
     * @return 对外VO
     */
    public static DatasetVersionVO from(EvaluationDatasetVersionEntity value) {
        return new DatasetVersionVO(value.getId(), value.getDatasetId(), value.getSpaceId(), value.getVersionNo(),
                value.getStatus(), value.getContentHash(), value.getPublishedAt(), value.getCreatedBy());
    }
}