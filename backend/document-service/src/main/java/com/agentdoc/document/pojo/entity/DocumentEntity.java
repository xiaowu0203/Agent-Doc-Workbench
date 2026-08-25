package com.agentdoc.document.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.document.pojo.vo.DocumentDetailVO;
import com.agentdoc.document.pojo.vo.DocumentTreeNodeVO;
import com.agentdoc.document.pojo.vo.DocumentVO;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档实体（树形目录 / 草稿正式双模式）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document")
@Schema(description = "文档实体")
public class DocumentEntity extends BaseLogicDeleteEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "父目录 ID，null 为根")
    private Long parentId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "Markdown 内容")
    private String content;

    @Schema(description = "类型：1 正式 / 2 草稿")
    private Integer docType;

    @Schema(description = "当前版本号（业务层维护，非乐观锁）")
    private Long version;

    @Schema(description = "状态：1 正常 / 0 归档")
    private Integer status;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;

    @Schema(description = "最后更新人用户 ID")
    private Long updatedBy;

    /**
     * 转换为列表视图对象（不含正文）。
     * @return 文档列表视图
     */
    public DocumentVO toVO() {
        return new DocumentVO(getId(), spaceId, parentId, title, DocType.fromCode(docType),
                version, DocStatus.fromCode(status), getUpdatedAt(), updatedBy);
    }

    /**
     * 转换为详情视图对象（含正文）。
     * @return 文档详情视图
     */
    public DocumentDetailVO toDetailVO() {
        return new DocumentDetailVO(getId(), spaceId, parentId, title, DocType.fromCode(docType),
                content, version, DocStatus.fromCode(status), getUpdatedAt(), updatedBy);
    }

    /**
     * 转换为树节点视图对象（子节点由服务层组装）。
     * @return 树节点视图
     */
    public DocumentTreeNodeVO toTreeNodeVO() {
        return DocumentTreeNodeVO.of(getId(), parentId, title, DocType.fromCode(docType));
    }

    /**
     * 转换为文档引用投影（服务间标题回填用）。
     * @return 文档引用投影
     */
    public DocumentRefVO toRefVO() {
        return new DocumentRefVO(getId(), spaceId, title);
    }

    /**
     * 转换为任务执行所需的文档上下文。
     */
    public DocumentExecutionContextVO toExecutionContextVO() {
        return new DocumentExecutionContextVO(getId(), spaceId, docType, status, version);
    }

    /**
     * 转换为文档合并结果。
     */
    public MergeResultVO toMergeResultVO() {
        return new MergeResultVO(getId(), title, version);
    }
}
