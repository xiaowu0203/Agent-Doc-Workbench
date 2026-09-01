package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.document.constant.DocumentConstant;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.document.mapper.DocumentDirectoryMapper;
import com.agentdoc.document.pojo.dto.DirectoryCreateDTO;
import com.agentdoc.document.pojo.dto.DirectoryMoveDTO;
import com.agentdoc.document.pojo.dto.DirectoryUpdateDTO;
import com.agentdoc.document.pojo.entity.DocumentDirectoryEntity;
import com.agentdoc.document.pojo.vo.DocumentDirectoryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;

/**
 * 文档目录服务。
 */
@Service
@RequiredArgsConstructor
public class DocumentDirectoryService {

    private final DocumentDirectoryMapper directoryMapper;
    private final SpacePermissionService permissionService;

    /**
     * 创建目录，目录最多支持三层。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDirectoryVO create(DirectoryCreateDTO dto) {
        permissionService.requirePermission(dto.spaceId(), DOCUMENT_EDIT);
        Long userId = permissionService.requireUserId();
        int depth = parentDepth(dto.spaceId(), dto.parentId()) + 1;
        if (depth > DocumentConstant.MAX_DIRECTORY_DEPTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "目录最多支持 " + DocumentConstant.MAX_DIRECTORY_DEPTH + " 层");
        }

        DocumentDirectoryEntity directory = new DocumentDirectoryEntity();
        directory.setSpaceId(dto.spaceId());
        directory.setParentId(dto.parentId());
        directory.setTitle(dto.title());
        directory.setStatus(DocStatus.NORMAL.getCode());
        directory.setCreatedBy(userId);
        directory.setUpdatedBy(userId);
        directoryMapper.insert(directory);
        return toVO(directory);
    }

    /**
     * 移动目录；目录下的文档通过 directory_id 自动随目录移动。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDirectoryVO move(Long id, DirectoryMoveDTO dto) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        if (!Objects.equals(directory.getStatus(), DocStatus.NORMAL.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录已归档，不能移动");
        }
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        Long newParentId = dto.parentId();
        if (Objects.equals(id, newParentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录不能移动到自身");
        }

        List<DocumentDirectoryEntity> directories = list(directory.getSpaceId(), DocStatus.NORMAL);
        Map<Long, DocumentDirectoryEntity> directoryMap = directories.stream()
                .collect(Collectors.toMap(DocumentDirectoryEntity::getId, item -> item));
        if (newParentId != null && !directoryMap.containsKey(newParentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标父目录不存在");
        }
        if (isDescendant(newParentId, id, directoryMap)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移动到自己的子目录下");
        }

        int newDepth = depth(newParentId, directoryMap) + 1;
        int subtreeHeight = subtreeHeight(id, directoryMap);
        if (newDepth + subtreeHeight - 1 > DocumentConstant.MAX_DIRECTORY_DEPTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "目录最多支持 " + DocumentConstant.MAX_DIRECTORY_DEPTH + " 层");
        }

        Long userId = permissionService.requireUserId();
        LocalDateTime updatedAt = LocalDateTime.now();
        directory.setParentId(newParentId);
        directory.setUpdatedBy(userId);
        directory.setUpdatedAt(updatedAt);
        directoryMapper.update(null, new LambdaUpdateWrapper<DocumentDirectoryEntity>()
                .eq(DocumentDirectoryEntity::getId, id)
                .set(DocumentDirectoryEntity::getParentId, newParentId)
                .set(DocumentDirectoryEntity::getUpdatedBy, userId)
                .set(DocumentDirectoryEntity::getUpdatedAt, updatedAt));
        return toVO(directory);
    }

    /**
     * 更新目录名称。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDirectoryVO update(Long id, DirectoryUpdateDTO dto) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        if (!Objects.equals(directory.getStatus(), DocStatus.NORMAL.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录已归档，不能修改");
        }
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        Long userId = permissionService.requireUserId();
        LocalDateTime updatedAt = LocalDateTime.now();
        directory.setTitle(dto.title());
        directory.setUpdatedBy(userId);
        directory.setUpdatedAt(updatedAt);
        directoryMapper.update(null, new LambdaUpdateWrapper<DocumentDirectoryEntity>()
                .eq(DocumentDirectoryEntity::getId, id)
                .eq(DocumentDirectoryEntity::getStatus, DocStatus.NORMAL.getCode())
                .set(DocumentDirectoryEntity::getTitle, dto.title())
                .set(DocumentDirectoryEntity::getUpdatedBy, userId)
                .set(DocumentDirectoryEntity::getUpdatedAt, updatedAt));
        return toVO(directory);
    }

    /**
     * 查询空间内指定状态的目录。
     */
    public List<DocumentDirectoryEntity> list(Long spaceId, DocStatus status) {
        return directoryMapper.selectList(new LambdaQueryWrapper<DocumentDirectoryEntity>()
                .eq(DocumentDirectoryEntity::getSpaceId, spaceId)
                .eq(DocumentDirectoryEntity::getStatus, status.getCode())
                .orderByAsc(DocumentDirectoryEntity::getCreatedAt)
                .orderByAsc(DocumentDirectoryEntity::getId));
    }

    /**
     * 校验并获取正常目录；directoryId 为 null 表示空间根层。
     */
    public DocumentDirectoryEntity requireNormal(Long spaceId, Long directoryId) {
        if (directoryId == null) {
            return null;
        }
        DocumentDirectoryEntity directory = directoryMapper.selectById(directoryId);
        if (directory == null || !Objects.equals(directory.getSpaceId(), spaceId)
                || !Objects.equals(directory.getStatus(), DocStatus.NORMAL.getCode())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标目录不存在");
        }
        return directory;
    }

    /**
     * 归档目录。
     */
    public void archive(Long id) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        directory.setStatus(DocStatus.ARCHIVED.getCode());
        directory.setUpdatedBy(permissionService.requireUserId());
        directoryMapper.updateById(directory);
    }

    /**
     * 恢复目录。
     */
    public void restore(Long id) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        directory.setStatus(DocStatus.NORMAL.getCode());
        directory.setUpdatedBy(permissionService.requireUserId());
        directoryMapper.updateById(directory);
    }

    private DocumentDirectoryEntity requireDirectory(Long id) {
        DocumentDirectoryEntity directory = directoryMapper.selectById(id);
        if (directory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目录不存在");
        }
        return directory;
    }

    private int parentDepth(Long spaceId, Long parentId) {
        int depth = 0;
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null && visited.add(current)) {
            DocumentDirectoryEntity directory = requireNormal(spaceId, current);
            depth++;
            current = directory.getParentId();
        }
        return depth;
    }

    private boolean isDescendant(Long candidateId, Long ancestorId,
                                 Map<Long, DocumentDirectoryEntity> directoryMap) {
        Set<Long> visited = new HashSet<>();
        Long current = candidateId;
        while (current != null && visited.add(current)) {
            if (Objects.equals(current, ancestorId)) {
                return true;
            }
            DocumentDirectoryEntity directory = directoryMap.get(current);
            current = directory == null ? null : directory.getParentId();
        }
        return false;
    }

    private int depth(Long directoryId, Map<Long, DocumentDirectoryEntity> directoryMap) {
        int depth = 0;
        Set<Long> visited = new HashSet<>();
        Long current = directoryId;
        while (current != null && visited.add(current)) {
            DocumentDirectoryEntity directory = directoryMap.get(current);
            if (directory == null) {
                break;
            }
            depth++;
            current = directory.getParentId();
        }
        return depth;
    }

    private int subtreeHeight(Long directoryId,
                              Map<Long, DocumentDirectoryEntity> directoryMap) {
        return directoryMap.values().stream()
                .filter(directory -> Objects.equals(directory.getParentId(), directoryId))
                .mapToInt(directory -> 1 + subtreeHeight(directory.getId(), directoryMap))
                .max()
                .orElse(1);
    }

    private DocumentDirectoryVO toVO(DocumentDirectoryEntity directory) {
        return new DocumentDirectoryVO(directory.getId(), directory.getSpaceId(), directory.getParentId(),
                directory.getTitle(), DocStatus.fromCode(directory.getStatus()), directory.getCreatedAt(),
                directory.getUpdatedAt());
    }
}
