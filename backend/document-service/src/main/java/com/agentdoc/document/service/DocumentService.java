package com.agentdoc.document.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
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
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.vo.DocumentDetailVO;
import com.agentdoc.document.pojo.vo.DocumentFragmentVO;
import com.agentdoc.document.pojo.vo.DocumentTreeNodeVO;
import com.agentdoc.document.pojo.vo.DocumentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DocumentVersionService versionService;
    private final SpacePermissionService permissionService;

    /**
     * 创建文档
     * 权限：EDITOR及以上；父目录必须属于同一个空间且真实存在
     *
     * @param dto 创建文档请求DTO
     * @return 文档简单视图VO
     */
    public DocumentVO create(DocumentCreateDTO dto) {
        // 获取当前登录用户ID，未登录直接抛出异常
        Long userId = permissionService.requireUserId();
        // 校验父目录合法性：同空间、父ID对应的文档存在
        validateParent(dto.spaceId(), dto.parentId());
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
     * @param spaceId 空间ID
     * @return 树形节点集合，返回所有一级根节点，节点内部携带children子节点
     */
    public List<DocumentTreeNodeVO> listTree(Long spaceId) {
        // 查询该空间下所有正常状态文档，按创建时间升序
        List<DocumentEntity> docs = documentMapper.selectList(new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getSpaceId, spaceId)
                .eq(DocumentEntity::getStatus, DocStatus.NORMAL.getCode())
                .orderByAsc(DocumentEntity::getCreatedAt));

        // 无文档直接返回空集合
        if (docs.isEmpty()) {
            return List.of();
        }

        // 将所有文档转换为树节点VO，key=文档ID
        Map<Long, DocumentTreeNodeVO> nodeMap = docs.stream()
                .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::toTreeNodeVO));
        // 根节点结果集合
        List<DocumentTreeNodeVO> roots = new ArrayList<>();
        // 遍历组装父子关系
        for (DocumentEntity doc : docs) {
            DocumentTreeNodeVO node = nodeMap.get(doc.getId());
            // parentId为null：一级根节点
            if (doc.getParentId() == null) {
                roots.add(node);
                continue;
            }
            // 找到父节点，加入父节点children列表
            DocumentTreeNodeVO parent = nodeMap.get(doc.getParentId());
            if (parent != null) {
                parent.children().add(node);
            } else {
                // 防御逻辑：父节点被删除/归档/不存在，该节点直接挂到根，避免树丢失节点
                roots.add(node);
            }
        }
        return roots;
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
        return doc.toDetailVO();
    }

    /**
     * 更新文档标题/内容
     * 权限：EDITOR及以上；正文发生变更自动生成版本快照
     * 事务：异常全部回滚
     *
     * @param id 待更新文档ID
     * @param dto 更新请求DTO
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
        // 将dto字段应用到实体
        dto.applyTo(doc);
        // 设置最后编辑人
        doc.setUpdatedBy(userId);
        // 更新数据库
        documentMapper.updateById(doc);
        // 如果传入了content，并且正文发生变化，则版本号自增，生成版本快照
        if (dto.content() != null && !Objects.equals(oldContent, dto.content())) {
            bumpVersion(doc, dto.content(), "编辑更新内容", userId);
        }
        return doc.toDetailVO();
    }

    /**
     * 移动文档（修改父目录）
     * 权限：EDITOR及以上；目标父目录必须同空间，禁止移动到自身或者自己后代节点下，防止循环树
     *
     * @param id 需要移动的文档ID
     * @param dto 移动请求，携带新的parentId
     * @return 移动完成后的文档简单VO
     */
    public DocumentVO move(Long id, DocumentMoveDTO dto) {
        // 校验文档存在
        DocumentEntity doc = requireDoc(id);
        // 校验编辑权限
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_EDIT);
        // 获取新的父节点ID
        Long newParentId = dto.parentId();

        // 如果新父节点不为null，做合法性校验
        if (newParentId != null) {
            // 根据父节点查询文档
            DocumentEntity parent = documentMapper.selectById(newParentId);
            // 父节点不存在，或者父节点不在同一个空间
            if (parent == null || !Objects.equals(parent.getSpaceId(), doc.getSpaceId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "目标父目录不存在");
            }
            // 禁止移动到自己下面
            if (Objects.equals(newParentId, doc.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移动到自身");
            }
            // 禁止移动到自己的后代节点，避免树出现循环
            if (isDescendant(newParentId, doc.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移动到自己的子目录下");
            }
        }
        // 更新父ID、更新人
        doc.setParentId(newParentId);
        doc.setUpdatedBy(permissionService.requireUserId());
        documentMapper.updateById(doc);
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
        return doc.toDetailVO();
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
     * 校验父目录合法性
     * 规则：parentId=null代表根节点直接放行；不为null时，父文档必须存在，并且和当前文档属于同一个空间
     *
     * @param spaceId 空间ID
     * @param parentId 父文档ID，null表示根目录
     */
    private void validateParent(Long spaceId, Long parentId) {
        // parentId为空，根目录，无需校验
        if (parentId == null) {
            return;
        }
        DocumentEntity parent = documentMapper.selectById(parentId);
        // 父文档不存在，或者父文档不属于当前空间
        if (parent == null || !Objects.equals(parent.getSpaceId(), spaceId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "父目录不存在");
        }
    }

    /**
     * 判断 candidateId 是否是 ancestorId 的后代节点
     * 用于移动文档防循环引用：向上遍历parent链，如果命中ancestorId说明是后代
     * 使用visited集合防御数据库脏数据导致死循环
     *
     * @param candidateId 待检测节点ID
     * @param ancestorId 疑似祖先节点ID
     * @return true candidateId是ancestorId后代；false 不是后代
     */
    private boolean isDescendant(Long candidateId, Long ancestorId) {
        Set<Long> visited = new HashSet<>();
        Long current = candidateId;
        while (current != null) {
            // 向上追溯找到了目标祖先，判定为后代
            if (current.equals(ancestorId)) {
                return true;
            }
            // 已经访问过该节点，出现循环链，直接跳出，防止死循环
            if (!visited.add(current)) {
                break;
            }
            DocumentEntity node = documentMapper.selectById(current);
            current = node == null ? null : node.getParentId();
        }
        return false;
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
