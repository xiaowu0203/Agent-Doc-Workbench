package com.agentdoc.document.pojo.dto;

import com.agentdoc.document.pojo.entity.DocumentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新文档请求参数（字段为空则不更新；内容变化时自动生成版本快照）。
 */
@Schema(description = "更新文档请求")
public record DocumentUpdateDTO(

        @Schema(description = "客户端读取文档时的版本号，用于乐观锁校验")
        @NotNull(message = "文档基线版本不能为空")
        Long baseVersion,

        @Schema(description = "文档标题")
        @Size(max = 200, message = "文档标题最长 200 字符")
        String title,

        @Schema(description = "Markdown 内容")
        String content
) {
    /**
     * 将非空字段应用到实体（局部更新，null 字段不覆盖）。
     * @param entity 目标文档实体
     */
    public void applyTo(DocumentEntity entity) {
        if (title != null) {
            entity.setTitle(title);
        }
        if (content != null) {
            entity.setContent(content);
        }
    }
}
