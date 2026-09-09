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
 * 文档草稿服务
 * 草稿存储在Redis，属于**用户级临时草稿**，不持久化到数据库；草稿有效期1天
 * 每个用户‑文档维度独立一份草稿，不同用户保存的草稿互不干扰
 */
@Service
@RequiredArgsConstructor
public class DocumentDraftService {
    // 草稿过期时间：1天，超过自动清除
    private static final Duration DRAFT_TTL = Duration.ofDays(1);

    private final DocumentService documentService;
    private final SpacePermissionService permissionService;
    private final RedisUtils redisUtils;

    /**
     * 获取当前用户针对指定文档的未提交草稿
     * 需要文档读取权限；缓存中无草稿返回null；缓存数据类型异常抛出内部错误
     *
     * @param documentId 文档ID
     * @return 草稿VO，不存在返回null
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
     * 保存当前用户的文档草稿到Redis
     * 需要文档编辑权限；保存后重置草稿TTL为1天；草稿不写入数据库
     *
     * @param documentId 文档ID
     * @param dto 草稿入参：基准版本、标题、正文内容
     * @return 保存后的草稿对象
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
     * 删除当前用户的文档草稿
     * 需要文档编辑权限；仅删除Redis缓存，不改动数据库文档
     *
     * @param documentId 文档ID
     */
    public void delete(Long documentId) {
        DocumentEntity document = documentService.requireDoc(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_EDIT);
        redisUtils.delete(key(document));
    }

    /**
     * 校验文档存在，并校验当前用户拥有文档读取权限
     * @param documentId 文档ID
     * @return 文档实体
     */
    private DocumentEntity requireReadableDocument(Long documentId) {
        DocumentEntity document = documentService.requireDoc(documentId);
        permissionService.requirePermission(document.getSpaceId(), DOCUMENT_READ);
        return document;
    }

    /**
     * 生成Redis草稿key
     * key维度：【当前用户ID + 空间ID + 文档ID】，做到每个用户对同一个文档拥有独立草稿
     * @param document 文档实体
     * @return redis缓存key
     */
    private String key(DocumentEntity document) {
        return RedisKeyConstants.documentDraftKey(
                permissionService.requireUserId(), document.getSpaceId(), document.getId());
    }
}
