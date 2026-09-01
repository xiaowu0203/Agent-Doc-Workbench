package com.agentdoc.document.service;

import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.document.pojo.dto.DocumentDraftSaveDTO;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.vo.DocumentDraftVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;

/**
 * 文档未提交草稿服务。
 * <p>草稿只存入 Redis，不生成文档版本；键按用户、空间和文档隔离，默认一天后过期。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentDraftService {

    private static final Duration DRAFT_TTL = Duration.ofDays(1);

    private final DocumentService documentService;
    private final SpacePermissionService permissionService;
    private final RedisUtils redisUtils;

    /**
     * 查询当前用户对指定文档的未提交草稿。
     *
     * @param documentId 文档 ID
     * @return 草稿；不存在时返回 null
     */
    public DocumentDraftVO get(Long documentId) {
        DocumentEntity document = requireReadableDocument(documentId);
        Object cached = redisUtils.get(key(document));
        if (cached == null) {
            return null;
        }
        if (cached instanceof DocumentDraftVO draft) {
            return draft;
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档草稿缓存格式异常");
    }

    /**
     * 保存当前用户的未提交草稿。
     *
     * @param documentId 文档 ID
     * @param dto 草稿内容
     * @return 已保存的草稿
     */
    public DocumentDraftVO save(Long documentId, DocumentDraftSaveDTO dto) {
        DocumentEntity document = documentService.requireDoc(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_EDIT);
        Long userId = permissionService.requireUserId();
        DocumentDraftVO draft = new DocumentDraftVO(documentId, dto.baseVersion(), dto.title(), dto.content());
        redisUtils.set(RedisKeyConstants.documentDraftKey(userId, document.getSpaceId(), documentId), draft, DRAFT_TTL);
        return draft;
    }

    /**
     * 删除当前用户的未提交草稿。
     *
     * @param documentId 文档 ID
     */
    public void delete(Long documentId) {
        DocumentEntity document = documentService.requireDoc(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_EDIT);
        redisUtils.delete(key(document));
    }

    private DocumentEntity requireReadableDocument(Long documentId) {
        DocumentEntity document = documentService.requireDoc(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_READ);
        return document;
    }

    private String key(DocumentEntity document) {
        return RedisKeyConstants.documentDraftKey(
                permissionService.requireUserId(), document.getSpaceId(), document.getId());
    }
}
