package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 变更请求批注视图。 */
@Schema(description = "变更请求批注")
public record ChangeRequestCommentVO(
        Long id,
        String changeKey,
        Long authorId,
        String authorName,
        String content,
        LocalDateTime createdAt) {
}
