package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 变更请求追加型批注。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("change_request_comment")
@Schema(description = "变更请求批注")
public class ChangeRequestCommentEntity extends BaseEntity {

    @Schema(description = "变更请求 ID")
    private Long changeRequestId;

    @Schema(description = "关联 Diff 块标识；空表示整单批注")
    private String changeKey;

    @Schema(description = "批注人用户 ID")
    private Long authorId;

    @Schema(description = "批注内容")
    private String content;
}
