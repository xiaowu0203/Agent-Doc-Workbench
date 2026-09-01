package com.agentdoc.document.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.PageUtils;
import com.agentdoc.document.constant.DocumentConstant;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.pojo.dto.DocumentCreateDTO;
import com.agentdoc.document.pojo.dto.DocumentMoveDTO;
import com.agentdoc.document.pojo.dto.DocumentUpdateDTO;
import com.agentdoc.document.pojo.entity.DocumentDirectoryEntity;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.param.DocumentRecentSearchParam;
import com.agentdoc.document.pojo.param.DocumentTreeSearchParam;
import com.agentdoc.document.pojo.vo.DocumentDetailVO;
import com.agentdoc.document.pojo.vo.DocumentFragmentVO;
import com.agentdoc.document.pojo.vo.DocumentStatsVO;
import com.agentdoc.document.pojo.vo.DocumentTreeNodeVO;
import com.agentdoc.document.pojo.vo.DocumentVO;
import com.agentdoc.document.pojo.vo.RecentDocumentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_MERGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_CREATE;

/**
 * 文档服务
 * 能力：文档CRUD、树形目录构建、草稿/正式双模式、文档移动、归档/恢复、回收站、版本快照、版本回滚
 * 权限约束：
 * 1. 查看（文档树/详情/回收站列表）：空间成员即可访问
 * 2. 创建/编辑/移动/归档/恢复：需要 EDITOR 及以上角色
 * 3. 正式文档禁止Agent直接修改，Agent通道Phase3必须走ChangeRequest审批流程；人工编辑不受该限制
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentDirectoryService directoryService;
    private final DocumentVersionService versionService;
    private final SpacePermissionService permissionService;
    private final AuthFeign authFeign;

    /**
     * 创建文档
     * 权限：EDITOR及以上；目标目录为空时文档创建在空间根层
     *
     * @param dto 创建文档请求DTO
     * @return 文档简单视图VO
     */
    public DocumentVO create(DocumentCreateDTO dto) {
        // 获取当前登录用户ID，未登录直接抛出异常
        Long userId = permissionService.requireUserId();
        // 目录 ID 为空表示空间根层，否则必须是同空间的正常目录。
        directoryService.requireNormal(dto.spaceId(), dto.directoryId());
        // DTO转数据库实体，设置创建人
        DocumentEntity doc = dto.toEntity(userId);
        // 入库
        documentMapper.insert(doc);
        return doc.toVO();
    }

    /**
     * 查询空间文档树
     * 权限：空间成员可读；只返回NORMAL正常状态文档，归档文档进入回收站，不在树形结构展示
     *
     * @param param 查询参数（空间、关键词、文档类型和状态）
     * @return 树形节点集合，返回所有一级根节点，节点内部携带children子节点
     */
    public List<DocumentTreeNodeVO> listTree(DocumentTreeSearchParam param) {
        // 文档与目录来自两张独立的表，先分别查询再组装为一棵树。
        DocStatus status = param.status() == null ? DocStatus.NORMAL : param.status();
        List<DocumentEntity> docs = documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getSpaceId, param.spaceId())
                .eq(DocumentEntity::getStatus, status.getCode())
                .orderByAsc(DocumentEntity::getCreatedAt));
        List<DocumentDirectoryEntity> directories = directoryService.list(param.spaceId(), status);
        String keyword = param.keyword() == null ? "" : param.keyword().trim().toLowerCase();
        Set<Long> matchedDocumentIds = docs.stream()
                .filter(doc -> keyword.isEmpty() || (doc.getTitle() != null
                        && doc.getTitle().toLowerCase().contains(keyword)))
                .filter(doc -> param.docType() == null
                        || Objects.equals(doc.getDocType(), param.docType().getCode()))
                .map(DocumentEntity::getId)
                .collect(Collectors.toSet());
        Set<Long> matchedDirectoryIds = directories.stream()
                .filter(directory -> keyword.isEmpty() || (directory.getTitle() != null
                        && directory.getTitle().toLowerCase().contains(keyword)))
                .map(DocumentDirectoryEntity::getId)
                .collect(Collectors.toSet());

        Map<Long, DocumentDirectoryEntity> directoryMap = directories.stream()
                .collect(Collectors.toMap(DocumentDirectoryEntity::getId, directory -> directory));
        Set<Long> includedDirectoryIds = new HashSet<>();
        if (keyword.isEmpty()) {
            includedDirectoryIds.addAll(directoryMap.keySet());
        } else {
            includedDirectoryIds.addAll(matchedDirectoryIds);
        }
        boolean directoryOnlySearch = !keyword.isEmpty()
                && !matchedDirectoryIds.isEmpty()
                && matchedDocumentIds.isEmpty();
        if (directoryOnlySearch) {
            for (DocumentDirectoryEntity directory : directories) {
                if (isDirectoryInSubtree(directory.getId(), matchedDirectoryIds, directoryMap)) {
                    includedDirectoryIds.add(directory.getId());
                }
            }
        }
        if (!directoryOnlySearch && (!keyword.isEmpty() || param.docType() != null)) {
            for (DocumentEntity document : docs) {
                if (!matchedDocumentIds.contains(document.getId())) {
                    continue;
                }
                includeDirectoryAncestors(document.getDirectoryId(), directoryMap, includedDirectoryIds);
            }
            for (Long directoryId : new HashSet<>(includedDirectoryIds)) {
                includeDirectoryAncestors(directoryMap.get(directoryId) == null
                        ? null : directoryMap.get(directoryId).getParentId(), directoryMap, includedDirectoryIds);
            }
        }

        Map<Long, DocumentTreeNodeVO> nodeMap = directories.stream()
                .filter(directory -> includedDirectoryIds.contains(directory.getId()))
                .collect(Collectors.toMap(DocumentDirectoryEntity::getId,
                        directory -> DocumentTreeNodeVO.ofDirectory(directory.getId(), directory.getParentId(),
                                directory.getTitle())));
        List<DocumentTreeNodeVO> roots = new ArrayList<>();
        for (DocumentDirectoryEntity directory : directories) {
            DocumentTreeNodeVO node = nodeMap.get(directory.getId());
            if (node == null) {
                continue;
            }
            DocumentTreeNodeVO parent = nodeMap.get(directory.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        for (DocumentEntity document : docs) {
            boolean matchedDocument = matchedDocumentIds.contains(document.getId());
            boolean belongsToMatchedDirectory = directoryOnlySearch
                    && document.getDirectoryId() != null
                    && includedDirectoryIds.contains(document.getDirectoryId())
                    && (param.docType() == null
                    || Objects.equals(document.getDocType(), param.docType().getCode()));
            if (!matchedDocument && !belongsToMatchedDirectory) {
                continue;
            }
            DocumentTreeNodeVO node = document.toTreeNodeVO();
            DocumentTreeNodeVO parent = nodeMap.get(document.getDirectoryId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    private void includeDirectoryAncestors(Long directoryId,
                                           Map<Long, DocumentDirectoryEntity> directoryMap,
                                           Set<Long> includedDirectoryIds) {
        Set<Long> visited = new HashSet<>();
        Long current = directoryId;
        while (current != null && visited.add(current)) {
            DocumentDirectoryEntity directory = directoryMap.get(current);
            if (directory == null) {
                break;
            }
            includedDirectoryIds.add(current);
            current = directory.getParentId();
        }
    }

    private boolean isDirectoryInSubtree(Long directoryId,
                                         Set<Long> rootDirectoryIds,
                                         Map<Long, DocumentDirectoryEntity> directoryMap) {
        Set<Long> visited = new HashSet<>();
        Long current = directoryId;
        while (current != null && visited.add(current)) {
            if (rootDirectoryIds.contains(current)) {
                return true;
            }
            DocumentDirectoryEntity directory = directoryMap.get(current);
            current = directory == null ? null : directory.getParentId();
        }
        return false;
    }

    /**
     * 查询空间最近更新的文档，分页返回。
     * 权限：空间成员可读；仅返回正常状态文档。
     *
     * @param param 查询参数（空间和分页）
     * @return 最近文档分页结果
     */
    public PageVO<RecentDocumentVO> listRecent(DocumentRecentSearchParam param) {
        permissionService.requirePermission(param.spaceId(), DOCUMENT_READ);
        PageParam pageParam = param.pageParam() == null ? new PageParam() : param.pageParam();
        pageParam.validate();

        Page<DocumentEntity> page = documentMapper.selectPage(
                PageUtils.toPage(pageParam),
                new LambdaQueryWrapper<DocumentEntity>()
                        .eq(DocumentEntity::getSpaceId, param.spaceId())
                        .eq(DocumentEntity::getStatus, DocStatus.NORMAL.getCode())
                        .orderByDesc(DocumentEntity::getUpdatedAt)
                        .orderByDesc(DocumentEntity::getId));
        Map<Long, UserRefVO> users = fetchUsers(page.getRecords().stream()
                .map(DocumentEntity::getUpdatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<RecentDocumentVO> records = page.getRecords().stream()
                .map(document -> new RecentDocumentVO(
                        document.getId(),
                        document.getTitle(),
                        DocType.fromCode(document.getDocType()),
                        document.getUpdatedAt(),
                        displayName(users.get(document.getUpdatedBy())))
                )
                .toList();
        return PageVO.of(records, page.getTotal(), pageParam);
    }

    private Map<Long, UserRefVO> fetchUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        var result = authFeign.queryUsers(new UserBatchQueryDTO(userIds));
        if (result == null || result.data() == null) {
            return Map.of();
        }
        List<UserRefVO> users = result.data();
        return users.stream().collect(Collectors.toMap(UserRefVO::id, user -> user));
    }

    private String displayName(UserRefVO user) {
        if (user == null) {
            return null;
        }
        if (user.nickname() != null && !user.nickname().isBlank()) {
            return user.nickname();
        }
        return user.username();
    }

    /**
     * 查询空间文档数量统计。
     * 当前总数与文档树保持一致，只统计正常且未逻辑删除的文档；历史数量按创建时间截取，
     * 由于文档表未保存历史状态，仅能统计当前仍为正常状态且在本月开始前创建的文档。
     *
     * @param spaceId 空间 ID
     * @return 当前总数与截至上月月底的数量
     */
    public DocumentStatsVO getStats(Long spaceId) {
        LocalDateTime currentMonthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LambdaQueryWrapper<DocumentEntity> currentDocuments = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getSpaceId, spaceId)
                .eq(DocumentEntity::getStatus, DocStatus.NORMAL.getCode())
                ;
        long totalCount = documentMapper.selectCount(currentDocuments);

        LambdaQueryWrapper<DocumentEntity> documentsAsOfLastMonth = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getSpaceId, spaceId)
                .eq(DocumentEntity::getStatus, DocStatus.NORMAL.getCode())
                .lt(DocumentEntity::getCreatedAt, currentMonthStart);
        long countAsOfLastMonth = documentMapper.selectCount(documentsAsOfLastMonth);
        return new DocumentStatsVO(totalCount, countAsOfLastMonth);
    }

    /**
     * 获取文档详情（包含正文内容）
     * 权限：空间成员可读
     *
     * @param id 文档主键ID
     * @return 文档详情VO，携带正文
     */
    public DocumentDetailVO detail(Long id) {
        // 校验文档必须存在，不存在抛404
        DocumentEntity doc = requireDoc(id);
        // 校验用户属于该空间成员
        if (AuthUtils.isAgent()) {
            permissionService.requireAgentCapability(doc.getSpaceId(), doc.getId(),
                    JwtConstant.ACTION_READ_FRAGMENT);
        } else {
            permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_READ);
        }
        return toDetailVO(doc);
    }

    /**
     * 更新文档标题/内容
     * 权限：EDITOR及以上；正文发生变更自动生成版本快照
     * 事务：异常全部回滚
     *
     * @param id 待更新文档ID
     * @param dto 更新请求DTO；按baseVersion执行乐观锁更新
     * @return 更新完成后的文档详情VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailVO update(Long id, DocumentUpdateDTO dto) {
        // 校验文档存在
        DocumentEntity doc = requireDoc(id);
        // 校验编辑权限
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_EDIT);
        Long userId = permissionService.requireUserId();

        // 更新前旧的文档正文，用于判断内容是否发生改变
        String oldContent = doc.getContent();
        checkBaseVersion(doc, dto.baseVersion());
        boolean contentChanged = dto.content() != null && !Objects.equals(oldContent, dto.content());
        long nextVersion = contentChanged
                ? (doc.getVersion() == null ? 0L : doc.getVersion()) + DocumentConstant.VERSION_INCREMENT
                : (doc.getVersion() == null ? 0L : doc.getVersion());
        // 将dto字段应用到实体
        dto.applyTo(doc);
        // 设置最后编辑人
        doc.setUpdatedBy(userId);
        // 更新数据库
        int updated = documentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, id)
                .eq(DocumentEntity::getVersion, dto.baseVersion())
                .set(dto.title() != null, DocumentEntity::getTitle, dto.title())
                .set(dto.content() != null, DocumentEntity::getContent, dto.content())
                .set(DocumentEntity::getUpdatedBy, userId)
                .set(contentChanged, DocumentEntity::getVersion, nextVersion));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档版本已变化，请刷新后重试");
        }
        doc.setVersion(nextVersion);
        // 如果传入了content，并且正文发生变化，则生成版本快照
        if (contentChanged) {
            versionService.createSnapshot(doc.getId(), nextVersion, dto.content(), "编辑更新内容", userId);
        }
        return toDetailVO(doc);
    }

    /**
     * 移动文档到指定目录；directoryId 为空表示空间根层。
     */
    public DocumentVO move(Long id, DocumentMoveDTO dto) {
        DocumentEntity doc = requireDoc(id);
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_EDIT);
        directoryService.requireNormal(doc.getSpaceId(), dto.directoryId());
        Long userId = permissionService.requireUserId();
        LocalDateTime updatedAt = LocalDateTime.now();
        doc.setDirectoryId(dto.directoryId());
        doc.setUpdatedBy(userId);
        doc.setUpdatedAt(updatedAt);
        documentMapper.update(null, new LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, id)
                .set(DocumentEntity::getDirectoryId, dto.directoryId())
                .set(DocumentEntity::getUpdatedBy, userId)
                .set(DocumentEntity::getUpdatedAt, updatedAt));
        return doc.toVO();
    }

    /**
     * 归档文档，移入回收站
     * 权限：EDITOR及以上
     *
     * @param id 文档ID
     */
    public void archive(Long id) {
        // 校验文档必须存在，不存在抛404
        DocumentEntity doc = requireDoc(id);
        // 校验编辑权限
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_EDIT);
        // 修改状态为归档（回收站）
        doc.setStatus(DocStatus.ARCHIVED.getCode());
        // 设置更新人
        doc.setUpdatedBy(permissionService.requireUserId());
        // 落库
        documentMapper.updateById(doc);
    }

    /**
     * 回收站恢复文档，从归档变回正常状态
     * 权限：EDITOR及以上
     *
     * @param id 文档ID
     */
    public void restore(Long id) {
        // 校验文档必须存在，不存在抛404
        DocumentEntity doc = requireDoc(id);
        // 校验编辑权限
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_EDIT);
        // 修改状态为正常
        doc.setStatus(DocStatus.NORMAL.getCode());
        doc.setUpdatedBy(permissionService.requireUserId());
        documentMapper.updateById(doc);
    }

    /**
     * 查询回收站归档文档列表，分页
     * 权限：EDITOR及以上
     *
     * @param spaceId 空间ID
     * @param pageParam 分页参数
     * @return 分页VO，返回归档文档
     */
    public PageVO<DocumentVO> trashList(Long spaceId, PageParam pageParam) {
        // 校验编辑权限
        permissionService.requirePermission(spaceId, DOCUMENT_READ);
        // 分页查询归档文档
        Page<DocumentEntity> page = documentMapper.selectPage(
                PageUtils.toPage(pageParam),
                new LambdaQueryWrapper<DocumentEntity>()
                        .eq(DocumentEntity::getSpaceId, spaceId)
                        .eq(DocumentEntity::getStatus, DocStatus.ARCHIVED.getCode())
                        .orderByDesc(DocumentEntity::getUpdatedAt));
        // 实体集合转VO，封装分页返回
        return PageVO.of(page.getRecords().stream().map(DocumentEntity::toVO).toList(),
                page.getTotal(), pageParam);
    }

    /**
     * 文档回滚历史版本
     * 权限：EDITOR及以上；不会删除历史快照，会生成一条全新版本记录
     * 事务：异常全部回滚
     *
     * @param id 文档ID
     * @param versionNo 需要回滚到的历史版本号
     * @return 回滚之后最新文档详情VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDetailVO rollback(Long id, Long versionNo) {
        // 校验文档必须存在，不存在抛404
        DocumentEntity doc = requireDoc(id);
        // 校验编辑权限
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_EDIT);
        // 获取当前用户ID
        Long userId = permissionService.requireUserId();
        // 获取目标历史版本快照，不存在抛异常
        var target = versionService.requireVersion(id, versionNo);
        // 将历史版本内容覆盖到当前文档
        doc.setContent(target.getContent());
        doc.setUpdatedBy(userId);
        documentMapper.updateById(doc);
        // 版本号+1，生成新版本快照，记录回滚操作摘要
        bumpVersion(doc, target.getContent(), "回滚至版本 " + versionNo, userId);
        return toDetailVO(doc);
    }

    private void checkBaseVersion(DocumentEntity doc, Long baseVersion) {
        if (baseVersion != null && !Objects.equals(baseVersion, doc.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档版本已变化，请刷新后重试");
        }
    }

    private DocumentDetailVO toDetailVO(DocumentEntity doc) {
        UserRefVO creator = doc.getCreatedBy() == null
                ? null
                : fetchUsers(List.of(doc.getCreatedBy())).get(doc.getCreatedBy());
        return doc.toDetailVO(displayName(creator));
    }

    /**
     * 版本号自增，并生成版本快照记录
     *
     * @param doc 文档数据库实体（内存中已读取version字段）
     * @param content 新版本文档正文
     * @param summary 变更描述摘要
     * @param userId 操作人ID
     */
    private void bumpVersion(DocumentEntity doc, String content, String summary, Long userId) {
        // 计算下一个版本号
        long nextVersion = doc.getVersion() + DocumentConstant.VERSION_INCREMENT;
        // 更新文档主表版本号
        doc.setVersion(nextVersion);
        documentMapper.updateById(doc);
        // 调用版本服务插入快照
        versionService.createSnapshot(doc.getId(), nextVersion, content, summary, userId);
    }

    /**
     * Feign调用入口：合并审批变更到正式文档（Task‑Service审批完成后调用）
     * 安全说明：操作人身份来自SecurityContext，由网关透传JWT，调用方不能伪造操作人
     * 业务逻辑：基线版本号校验防并发覆盖；应用结构化变更；生成新版本快照
     * 事务：异常全部回滚
     *
     * @param request 合并变更请求DTO，携带文档ID、基线版本、变更项、变更摘要
     * @return MergeResultVO 返回文档ID、标题、合并后新版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public MergeResultVO mergeForFeign(MergeRequestDTO request) {
        DocumentEntity doc = requireDoc(request.documentId());
        // 合并操作等同于编辑文档，需要EDITOR权限
        permissionService.requirePermission(doc.getSpaceId(), CHANGE_REQUEST_MERGE);
        Long operatorId = permissionService.requireUserId();
        // 并发保护：客户端传入的基线版本号必须等于数据库当前版本，否则拒绝合并，防止覆盖别人编辑内容
        if (!Objects.equals(request.baseVersion(), doc.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档基线版本不匹配，请基于最新版本重新生成变更");
        }
        // 将结构化变更应用到文档正文，生成新内容
        String newContent = applyChanges(doc.getContent(), request.changes());
        doc.setContent(newContent);
        doc.setUpdatedBy(operatorId);
        documentMapper.updateById(doc);

        // 设置变更摘要，为空时使用默认文案
        String summary = request.changeSummary() == null || request.changeSummary().isBlank()
                ? "审批合并变更" : request.changeSummary();
        // 版本号+1，写入快照
        bumpVersion(doc, newContent, summary, operatorId);
        return doc.toMergeResultVO();
    }

    /**
     * 按顺序应用结构化变更项
     * v0.1版本支持两种操作：REPLACE全文替换 / APPEND末尾追加文本
     *
     * @param current 当前文档Markdown正文，允许null
     * @param changes 变更操作集合，顺序执行
     * @return 执行完成之后的新文档文本
     */
    private String applyChanges(String current, List<ChangeItemDTO> changes) {
        // null转为空字符串
        String content = current == null ? "" : current;
        for (ChangeItemDTO item : changes) {
            if (item.op() == ChangeOp.REPLACE) {
                // 全文替换，直接覆盖全部内容
                content = item.newText();
            } else if (item.op() == ChangeOp.APPEND) {
                // 在文档末尾追加文本
                content = content + item.newText();
            } else {
                // 不支持的操作类型抛出参数异常
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的变更操作");
            }
        }
        return content;
    }

    /**
     * 读取文档片段（字符偏移读取）
     * 面向MCP Agent使用，按需加载部分文档，用来控制Token消耗；空间成员可读
     * 边界：start超出文档总长度返回空；length超过剩余内容会自动截断；起始偏移强制>=0
     *
     * @param id 文档ID
     * @param start 起始字符偏移，从0开始
     * @param length 请求读取字符长度，受FRAGMENT_MAX_LENGTH常量上限约束
     * @return DocumentFragmentVO 返回片段内容、实际偏移、实际读取字符数、文档总字符数
     */
    public DocumentFragmentVO readFragment(Long id, long start, int length) {
        DocumentEntity doc = requireDoc(id);
        // 若为Agent发起的请求，则按【校验 Agent 任务能力令牌、操作权限】
        if (AuthUtils.isAgent()) {
            permissionService.requireAgentCapability(doc.getSpaceId(), doc.getId(),
                    JwtConstant.ACTION_READ_FRAGMENT);
        } else {
            // 若为普通用户发起的请求，则校验用户是否拥有该空间的查看权限
            permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_READ);
        }
        String content = doc.getContent() == null ? "" : doc.getContent();
        long total = content.length();

        // 安全边界修正：起始位置不能小于0，不能超过文档总长度
        long safeStart = Math.min(Math.max(start, 0), total);
        // 安全读取长度：不能小于0，不能超过剩余字符
        int safeLength = (int) Math.min(Math.max(length, 0), total - safeStart);
        String fragment = safeLength == 0 ? "" : content.substring((int) safeStart, (int) safeStart + safeLength);
        return new DocumentFragmentVO(id, fragment, safeStart, safeLength, total);
    }

    /**
     * 返回任务创建所需的文档上下文，并要求当前用户具备编辑权限。
     */
    public DocumentExecutionContextVO getExecutionContext(Long id) {
        // 校验文档必须存在，不存在抛404
        DocumentEntity doc = requireDoc(id);
        if (AuthUtils.isAgent()) {
            // 若当前请求为Agent，校验 Agent 是否拥有【阅读片段】能力
            permissionService.requireAgentCapability(doc.getSpaceId(), doc.getId(),
                    JwtConstant.ACTION_READ_FRAGMENT);
        } else {
            // 校验编辑权限
            permissionService.requirePermission(doc.getSpaceId(), TASK_CREATE);
        }
        return doc.toExecutionContextVO();
    }

    /**
     * 将 Agent 变更直接应用到草稿文档，正式文档不允许走此入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public MergeResultVO applyAgentDraftChanges(MergeRequestDTO request) {
        // 校验文档必须存在，不存在抛404
        DocumentEntity doc = requireDoc(request.documentId());
        // 校验 Agent 任务能力令牌
        permissionService.requireAgentCapability(doc.getSpaceId(), doc.getId(),
                JwtConstant.ACTION_WRITE_DRAFT);
        // 若文档状态非【草稿】，抛出异常
        if (doc.getDocType() != DocType.DRAFT.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "正式文档的 Agent 变更必须进入审批队列");
        }
        // 并发保护：客户端传入的基线版本号必须等于数据库当前版本，否则拒绝合并，防止覆盖别人编辑内容
        if (!Objects.equals(request.baseVersion(), doc.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档基线版本不匹配，请重新读取后生成变更");
        }
        // 构建新内容（全文替换/追加）
        String newContent = applyChanges(doc.getContent(), request.changes());
        // 设置并更新文档
        doc.setContent(newContent);
        doc.setUpdatedBy(AuthUtils.getAgentIdOrException());
        documentMapper.updateById(doc);
        // 版本号+1，生成新版本快照，记录回滚操作摘要
        bumpVersion(doc, newContent,
                request.changeSummary() == null ? "Agent 更新草稿" : request.changeSummary(),
                doc.getUpdatedBy());
        return doc.toMergeResultVO();
    }

    /**
     * 批量查询文档引用投影信息
     * 返回：id、spaceId、title；登录即可访问，标题属于非敏感信息；用于跨服务标题回填展示
     * 注意：传入ID不存在的不会出现在返回列表
     *
     * @param ids 文档ID集合，允许空
     * @return 文档轻量投影VO集合
     */
    public List<DocumentRefVO> listRefs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<DocumentEntity> documents = documentMapper.selectBatchIds(ids);
        documents.stream()
                .map(DocumentEntity::getSpaceId)
                .distinct()
                .forEach(spaceId -> permissionService.requirePermission(spaceId, DOCUMENT_READ));
        return documents.stream()
                .map(DocumentEntity::toRefVO)
                .toList();
    }

    /**
     * 查询指定空间下全部文档ID集合
     * 服务间调用，用于审批队列按空间过滤；权限：空间成员
     *
     * @param spaceId 空间ID
     * @return 该空间下所有文档主键ID列表
     */
    public List<Long> listIdsBySpace(Long spaceId) {
        permissionService.requirePermission(spaceId, DOCUMENT_READ);
        return documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                        .eq(DocumentEntity::getSpaceId, spaceId)
                        .select(DocumentEntity::getId))
                .stream()
                .map(DocumentEntity::getId)
                .toList();
    }

    /**
     * 根据ID获取文档实体，如果不存在抛出NOT_FOUND业务异常
     *
     * @param id 文档主键ID
     * @return DocumentEntity 数据库实体
     */
    public DocumentEntity requireDoc(Long id) {
        DocumentEntity doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }
}
