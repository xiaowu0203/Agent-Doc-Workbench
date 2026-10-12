package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** 一个实验/人类用户的首次知情事实；后续访问不覆盖。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_review_exposure")
public class OnlineReviewExposureEntity extends BaseEntity {
    @Schema(description = "实验身份") private Long experimentId;
    @Schema(description = "空间身份") private Long spaceId;
    @Schema(description = "人类用户身份") private Long actorId;
    @Schema(description = "首次访问来源") private String source;
    @Schema(description = "首次访问时间，毫秒精度") private LocalDateTime capturedAt;
    @Schema(description = "不可变来源摘要") private String sourceHash;
}
