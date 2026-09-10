package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;

/**
 * 文档目录服务
 * 提供目录的创建、移动、重命名、归档、恢复；支持树形层级校验，限制最大目录深度；
 * 目录移动时，目录下文档依靠directory_id关联，跟随目录一起迁移
 */
@Service
@RequiredArgsConstructor
public class DocumentDirectoryService {

    private final DocumentDirectoryMapper directoryMapper;
    private final SpacePermissionService permissionService;

    /**
     * 创建文档目录
     * 校验目录层级不能超过最大限制；创建成功返回目录VO
     *
     * @param dto 创建参数：空间ID、父目录ID、目录标题
     * @return 新建目录VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDirectoryVO create(DirectoryCreateDTO dto) {
        // 校验用户拥有空间文档编辑权限
        permissionService.requirePermission(dto.spaceId(), DOCUMENT_EDIT);
        Long userId = permissionService.requireUserId();

        // 计算父目录深度，+1得到当前新建目录层级
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
     * 移动目录
     * 注意：目录下的文档通过directory_id外键，会跟随目录一起移动，无需修改文档数据
     * 校验规则：
     * 1. 目录必须为正常状态，归档目录不可移动
     * 2. 不能将目录移动到自身
     * 3. 不能移动到自身的子目录下（防止树形循环）
     * 4. 移动后整个子树不能超出最大目录深度限制
     *
     * @param id 待移动目录ID
     * @param dto 移动参数：新父目录ID
     * @return 移动后的目录VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDirectoryVO move(Long id, DirectoryMoveDTO dto) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        // 归档目录禁止移动
        if (!Objects.equals(directory.getStatus(), DocStatus.NORMAL.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录已归档，不能移动");
        }
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        Long newParentId = dto.parentId();
        // 禁止移动到自己本身
        if (Objects.equals(id, newParentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录不能移动到自身");
        }

        // 查询该空间全部正常目录，构建id->实体map，用于树形计算
        List<DocumentDirectoryEntity> directories = list(directory.getSpaceId(), DocStatus.NORMAL);
        Map<Long, DocumentDirectoryEntity> directoryMap = directories.stream()
                .collect(Collectors.toMap(DocumentDirectoryEntity::getId, item -> item));

        // 校验目标父目录是否存在
        if (newParentId != null && !directoryMap.containsKey(newParentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标父目录不存在");
        }
        // 校验：目标父目录不能是当前目录的后代，避免循环树形结构
        if (isDescendant(newParentId, id, directoryMap)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移动到自己的子目录下");
        }

        // 计算移动后的新深度，以及该目录整个子树的高度
        int newDepth = depth(newParentId, directoryMap) + 1;
        int subtreeHeight = subtreeHeight(id, directoryMap);
        // 校验移动后子树最底层不能超过最大目录深度
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
     * 更新目录名称
     * 归档目录禁止修改
     *
     * @param id 目录ID
     * @param dto 更新参数：新标题
     * @return 更新后目录VO
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
     * 查询空间内指定状态的全部目录
     * @param spaceId 空间ID
     * @param status 目录状态
     * @return 目录实体列表
     */
    public List<DocumentDirectoryEntity> list(Long spaceId, DocStatus status) {
        return directoryMapper.selectList(new LambdaQueryWrapper<DocumentDirectoryEntity>()
                .eq(DocumentDirectoryEntity::getSpaceId, spaceId)
                .eq(DocumentDirectoryEntity::getStatus, status.getCode())
                .orderByAsc(DocumentDirectoryEntity::getCreatedAt)
                .orderByAsc(DocumentDirectoryEntity::getId));
    }

    /**
     * 校验获取正常可用目录
     * directoryId为null代表空间根目录（无父节点），直接返回null
     *
     * @param spaceId 空间ID
     * @param directoryId 目录ID
     * @return 目录实体；null代表根层
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
     * 归档目录，将状态改为ARCHIVED
     * 归档后目录不可移动、修改；子目录与文档不受本方法直接影响
     * @param id 目录ID
     */
    public void archive(Long id) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        directory.setStatus(DocStatus.ARCHIVED.getCode());
        directory.setUpdatedBy(permissionService.requireUserId());
        directoryMapper.updateById(directory);
    }

    /**
     * 恢复归档目录，状态切回NORMAL
     * @param id 目录ID
     */
    public void restore(Long id) {
        DocumentDirectoryEntity directory = requireDirectory(id);
        permissionService.requirePermission(directory.getSpaceId(), DOCUMENT_EDIT);
        directory.setStatus(DocStatus.NORMAL.getCode());
        directory.setUpdatedBy(permissionService.requireUserId());
        directoryMapper.updateById(directory);
    }

    /**
     * 分页查询当前空间已归档目录。
     *
     * @param spaceId 空间 ID
     * @param pageParam 分页参数
     * @return 归档目录分页结果
     */
    public PageVO<DocumentDirectoryVO> trashList(Long spaceId, PageParam pageParam) {
        permissionService.requirePermission(spaceId, DOCUMENT_READ);
        Page<DocumentDirectoryEntity> page = directoryMapper.selectPage(
                new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new LambdaQueryWrapper<DocumentDirectoryEntity>()
                        .eq(DocumentDirectoryEntity::getSpaceId, spaceId)
                        .eq(DocumentDirectoryEntity::getStatus, DocStatus.ARCHIVED.getCode())
                        .orderByDesc(DocumentDirectoryEntity::getUpdatedAt));
        Set<Long> parentIds = page.getRecords().stream()
                .map(DocumentDirectoryEntity::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, DocumentDirectoryEntity> parentDirectories = parentIds.isEmpty()
                ? Map.of()
                : directoryMapper.selectList(new LambdaQueryWrapper<DocumentDirectoryEntity>()
                                .eq(DocumentDirectoryEntity::getSpaceId, spaceId)
                                .in(DocumentDirectoryEntity::getId, parentIds))
                        .stream()
                        .collect(Collectors.toMap(DocumentDirectoryEntity::getId, directory -> directory));
        return PageVO.of(page.getRecords().stream()
                .map(directory -> toVO(directory, parentDirectories.get(directory.getParentId())))
                .toList(), page.getTotal(), pageParam);
    }

    /**
     * 根据id查询目录，不存在抛出异常
     * @param id 目录ID
     * @return 目录实体
     */
    private DocumentDirectoryEntity requireDirectory(Long id) {
        DocumentDirectoryEntity directory = directoryMapper.selectById(id);
        if (directory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目录不存在");
        }
        return directory;
    }

    /**
     * 递归计算父目录层级深度
     * @param spaceId 空间ID
     * @param parentId 父目录ID
     * @return 父目录的深度数值
     */
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

    /**
     * 判断 candidateId 是否是 ancestorId 的后代目录（子/孙等）
     * @param candidateId 待校验目录ID
     * @param ancestorId 祖先目录ID
     * @param directoryMap 目录id映射表
     * @return true：是后代；false：不是
     */
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

    /**
     * 计算指定目录的层级深度
     * @param directoryId 目录ID
     * @param directoryMap 目录id映射表
     * @return 深度数值
     */
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

    /**
     * 递归计算该目录下整个子树的最大高度（子树最深层级）
     * @param directoryId 当前目录ID
     * @param directoryMap 目录id映射表
     * @return 子树高度
     */
    private int subtreeHeight(Long directoryId,
                              Map<Long, DocumentDirectoryEntity> directoryMap) {
        return directoryMap.values().stream()
                .filter(directory -> Objects.equals(directory.getParentId(), directoryId))
                .mapToInt(directory -> 1 + subtreeHeight(directory.getId(), directoryMap))
                .max()
                .orElse(1);
    }

    /**
     * 目录实体转换为VO
     * @param directory 目录实体
     * @return 目录VO
     */
    private DocumentDirectoryVO toVO(DocumentDirectoryEntity directory) {
        return new DocumentDirectoryVO(directory.getId(), directory.getSpaceId(), directory.getParentId(),
                directory.getTitle(), DocStatus.fromCode(directory.getStatus()), directory.getCreatedAt(),
                directory.getUpdatedAt(), null, null);
    }

    private DocumentDirectoryVO toVO(DocumentDirectoryEntity directory, DocumentDirectoryEntity parentDirectory) {
        return new DocumentDirectoryVO(directory.getId(), directory.getSpaceId(), directory.getParentId(),
                directory.getTitle(), DocStatus.fromCode(directory.getStatus()), directory.getCreatedAt(),
                directory.getUpdatedAt(), parentDirectory == null ? null : parentDirectory.getTitle(),
                parentDirectory == null ? null : DocStatus.fromCode(parentDirectory.getStatus()));
    }
}
