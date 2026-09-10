package com.agentdoc.document.pojo.vo;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.document.enums.DocumentNodeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档树节点视图对象。
 */
@Schema(description = "文档树节点")
public record DocumentTreeNodeVO(

        @Schema(description = "文档 ID")
        Long id,

        @Schema(description = "父目录 ID，null 为根")
        Long parentId,

        @Schema(description = "标题")
        String title,

        @Schema(description = "文档类型；目录节点为空")
        DocType docType,

        @Schema(description = "节点类型")
        DocumentNodeType nodeType,

        @Schema(description = "子节点列表")
        List<DocumentTreeNodeVO> children
) {

    /**
     * 构造带子节点容器的节点。
     * @param id 文档 ID
     * @param parentId 父目录 ID
     * @param title 标题
     * @param docType 文档类型
     * @return 树节点（children 为空列表）
     */
    public static DocumentTreeNodeVO ofDocument(Long id, Long directoryId, String title, DocType docType) {
        return new DocumentTreeNodeVO(id, directoryId, title, docType, DocumentNodeType.DOCUMENT,
                new ArrayList<>());
    }

    public static DocumentTreeNodeVO ofDirectory(Long id, Long parentId, String title) {
        return new DocumentTreeNodeVO(id, parentId, title, null, DocumentNodeType.DIRECTORY,
                new ArrayList<>());
    }
}
