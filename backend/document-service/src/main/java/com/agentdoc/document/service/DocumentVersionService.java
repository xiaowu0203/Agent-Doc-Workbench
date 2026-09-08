package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.DocumentVersionRollbackAuditDTO;
import com.agentdoc.common.feign.dto.DocumentVersionSourceQueryDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.DocumentVersionSourceVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.PageUtils;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.mapper.DocumentVersionMapper;
import com.agentdoc.document.enums.DocumentVersionActorType;
import com.agentdoc.document.enums.DocumentVersionSourceType;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.entity.DocumentVersionEntity;
import com.agentdoc.document.pojo.vo.DocumentVersionDetailVO;
import com.agentdoc.document.pojo.vo.DocumentVersionVO;
import com.agentdoc.document.pojo.vo.VersionCompareVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;

/**
 * 文档版本服务
 * 能力：版本快照生成、版本分页列表、版本详情、版本对比，为文档回滚提供底层支撑
 * 版本规则：
 * 1. 创建文档时保存 v0 基线，后续内容变更、审批合并、版本回滚时持续递增
 * 2. 回滚操作会生成全新版本快照，不会修改、删除任何历史版本记录
 * 权限：版本属于文档子资源，读取版本信息需要为所属空间成员
 */
@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private final DocumentVersionMapper versionMapper;
    private final DocumentMapper documentMapper;
    private final SpacePermissionService permissionService;
    private final TaskFeign taskFeign;
    private final AuthFeign authFeign;

    /** 创建文档初始版本。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionVO createInitialSnapshot(Long documentId, Long versionNo, String content,
                                                   String changeSummary, Long userId) {
        return createSnapshot(documentId, versionNo, content, changeSummary,
                DocumentVersionSourceType.CREATE, DocumentVersionActorType.HUMAN, userId,
                null, null, null);
    }

    /** 创建人工编辑版本。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionVO createHumanEditSnapshot(Long documentId, Long versionNo, String content,
                                                     String changeSummary, Long userId) {
        return createSnapshot(documentId, versionNo, content, changeSummary,
                DocumentVersionSourceType.HUMAN_EDIT, DocumentVersionActorType.HUMAN, userId,
                null, null, null);
    }

    /** 创建 Agent 草稿提交版本。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionVO createAgentDraftSnapshot(Long documentId, Long versionNo, String content,
                                                      String changeSummary, Long agentId, Long taskId) {
        return createSnapshot(documentId, versionNo, content, changeSummary,
                DocumentVersionSourceType.AGENT_DRAFT, DocumentVersionActorType.AGENT, agentId,
                null, taskId, null);
    }

    /** 创建带审批与任务来源的版本。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionVO createApprovalSnapshot(Long documentId, Long versionNo, String content,
                                                    String changeSummary, Long userId,
                                                    Long changeRequestId, Long taskId) {
        return createSnapshot(documentId, versionNo, content, changeSummary,
                DocumentVersionSourceType.APPROVAL_MERGE, DocumentVersionActorType.HUMAN, userId,
                changeRequestId, taskId, null);
    }

    /** 创建回滚版本，并返回持久化实体供审计关联。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionEntity createRollbackSnapshot(Long documentId, Long versionNo, String content,
                                                        String changeSummary, Long userId,
                                                        Long rollbackFromVersion) {
        return insertSnapshot(documentId, versionNo, content, changeSummary,
                DocumentVersionSourceType.ROLLBACK, DocumentVersionActorType.HUMAN, userId,
                null, null, rollbackFromVersion);
    }

    /** 将已完成的回滚写入统一审计日志。 */
    public void recordRollbackAudit(Long spaceId, DocumentVersionEntity version) {
        requireData(taskFeign.recordDocumentVersionRollback(new DocumentVersionRollbackAuditDTO(
                spaceId, version.getDocumentId(), version.getId(), version.getVersionNo(),
                version.getRollbackFromVersion())));
    }

    private DocumentVersionVO createSnapshot(Long documentId, Long versionNo, String content,
                                             String changeSummary, DocumentVersionSourceType sourceType,
                                             DocumentVersionActorType actorType, Long actorId,
                                             Long changeRequestId, Long taskId, Long rollbackFromVersion) {
        return toVO(insertSnapshot(documentId, versionNo, content, changeSummary, sourceType, actorType,
                actorId, changeRequestId, taskId, rollbackFromVersion), null, null);
    }

    private DocumentVersionEntity insertSnapshot(Long documentId, Long versionNo, String content,
                                                 String changeSummary, DocumentVersionSourceType sourceType,
                                                 DocumentVersionActorType actorType, Long actorId,
                                                 Long changeRequestId, Long taskId, Long rollbackFromVersion) {
        DocumentVersionEntity entity = DocumentVersionEntity.create(
                documentId, versionNo, content, changeSummary, sourceType.name(), actorType.name(), actorId,
                changeRequestId, taskId, rollbackFromVersion, sha256(content));
        versionMapper.insert(entity);
        return entity;
    }

    /** 按变更请求幂等键查找已生成版本。 */
    public DocumentVersionEntity findByChangeRequestId(Long changeRequestId) {
        if (changeRequestId == null) {
            return null;
        }
        return versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getSourceChangeRequestId, changeRequestId));
    }

    /**
     * 查询文档版本分页列表
     * 权限：空间成员可读；按版本号倒序（最新版本排在最前面）；返回VO不含大文本正文快照，用于版本历史列表展示
     *
     * @param documentId 文档ID
     * @param pageParam 分页参数
     * @return 分页对象，版本列表（无正文）
     */
    public PageVO<DocumentVersionVO> listVersions(Long documentId, PageParam pageParam) {
        DocumentEntity document = checkReadable(documentId);
        pageParam.validate();
        Page<DocumentVersionEntity> page = versionMapper.selectPage(
                PageUtils.toPage(pageParam),
                new LambdaQueryWrapper<DocumentVersionEntity>()
                        .eq(DocumentVersionEntity::getDocumentId, documentId)
                        .orderByDesc(DocumentVersionEntity::getVersionNo));
        VersionContext context = loadContext(document.getSpaceId(), page.getRecords());
        return PageVO.of(page.getRecords().stream()
                        .map(version -> toVO(version, context.source(version), context.actorName(version)))
                        .toList(),
                page.getTotal(), pageParam);
    }

    /**
     * 获取指定版本完整详情，包含该版本的正文快照
     * 权限：空间成员可读
     *
     * @param documentId 文档ID
     * @param versionNo 目标版本号
     * @return 版本详情VO，携带完整Markdown正文快照
     */
    public DocumentVersionDetailVO versionDetail(Long documentId, Long versionNo) {
        DocumentEntity document = checkReadable(documentId);
        DocumentVersionEntity version = requireVersion(documentId, versionNo);
        VersionContext context = loadContext(document.getSpaceId(), List.of(version));
        return toDetailVO(version, context.source(version), context.actorName(version));
    }

    /**
     * 两个版本对比（v0.1简化实现）
     * 后端只取出源版本、目标版本两份完整快照文本；diff文本高亮差异计算交给前端完成
     *
     * @param documentId 文档ID
     * @param fromVersionNo 对比源版本号（旧版本）
     * @param toVersionNo 对比目标版本号（新版本）
     * @return 版本对比VO，封装源版本与目标版本完整快照数据
     */
    public VersionCompareVO compare(Long documentId, Long fromVersionNo, Long toVersionNo) {
        DocumentEntity document = checkReadable(documentId);
        DocumentVersionEntity from = requireVersion(documentId, fromVersionNo);
        DocumentVersionEntity to = requireVersion(documentId, toVersionNo);
        VersionContext context = loadContext(document.getSpaceId(), List.of(from, to));
        return new VersionCompareVO(
                toDetailVO(from, context.source(from), context.actorName(from)),
                toDetailVO(to, context.source(to), context.actorName(to)));
    }

    private VersionContext loadContext(Long spaceId, List<DocumentVersionEntity> versions) {
        List<Long> changeRequestIds = versions.stream().map(DocumentVersionEntity::getSourceChangeRequestId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> taskIds = versions.stream().map(DocumentVersionEntity::getSourceTaskId)
                .filter(Objects::nonNull).distinct().toList();
        List<DocumentVersionSourceVO> sourceRows = changeRequestIds.isEmpty() && taskIds.isEmpty()
                ? List.of()
                : requireData(taskFeign.queryDocumentVersionSources(
                        new DocumentVersionSourceQueryDTO(spaceId, changeRequestIds, taskIds)));
        Map<Long, DocumentVersionSourceVO> byChangeRequest = new HashMap<>();
        Map<Long, DocumentVersionSourceVO> byTask = new HashMap<>();
        if (sourceRows != null) {
            for (DocumentVersionSourceVO source : sourceRows) {
                if (source.sourceChangeRequestId() != null) {
                    byChangeRequest.putIfAbsent(source.sourceChangeRequestId(), source);
                }
                if (source.sourceTaskId() != null) {
                    byTask.putIfAbsent(source.sourceTaskId(), source);
                }
            }
        }
        List<Long> humanActorIds = versions.stream()
                .filter(version -> actorType(version) == DocumentVersionActorType.HUMAN)
                .map(DocumentVersionEntity::getActorId).filter(Objects::nonNull).distinct().toList();
        Map<Long, UserRefVO> users = humanActorIds.isEmpty() ? Map.of()
                : toUserMap(requireData(authFeign.queryUsers(new UserBatchQueryDTO(humanActorIds))));
        return new VersionContext(byChangeRequest, byTask, users);
    }

    private DocumentVersionVO toVO(DocumentVersionEntity version, DocumentVersionSourceVO source,
                                   String actorName) {
        return new DocumentVersionVO(version.getId(), version.getDocumentId(), version.getVersionNo(),
                version.getChangeSummary(), sourceType(version), actorType(version), version.getActorId(), actorName,
                version.getCreatedBy(),
                version.getSourceChangeRequestId(), version.getSourceTaskId(), source == null ? null : source.taskNo(),
                source == null ? null : source.taskName(), source == null ? null : source.agentId(),
                source == null ? null : source.agentName(), source == null ? null : source.triggeredBy(),
                source == null ? null : source.triggeredByName(), source == null ? null : source.tokensUsed(),
                source == null ? null : source.tokensEstimated(), source == null ? null : source.reviewedBy(),
                source == null ? null : source.reviewedByName(), source == null ? null : source.reviewedAt(),
                source == null ? null : source.mergedBy(), source == null ? null : source.mergedByName(),
                source == null ? null : source.mergedAt(), version.getRollbackFromVersion(),
                version.getContentSha256(), source != null && Boolean.TRUE.equals(source.executionAvailable()),
                version.getCreatedAt());
    }

    private DocumentVersionDetailVO toDetailVO(DocumentVersionEntity version, DocumentVersionSourceVO source,
                                               String actorName) {
        return new DocumentVersionDetailVO(version.getDocumentId(), version.getVersionNo(), version.getContent(),
                version.getChangeSummary(), sourceType(version), actorType(version), version.getActorId(), actorName,
                version.getCreatedBy(),
                version.getSourceChangeRequestId(), version.getSourceTaskId(), source == null ? null : source.taskNo(),
                source == null ? null : source.taskName(), source == null ? null : source.agentId(),
                source == null ? null : source.agentName(), source == null ? null : source.triggeredBy(),
                source == null ? null : source.triggeredByName(), source == null ? null : source.tokensUsed(),
                source == null ? null : source.tokensEstimated(), source == null ? null : source.reviewedBy(),
                source == null ? null : source.reviewedByName(), source == null ? null : source.reviewedAt(),
                source == null ? null : source.mergedBy(), source == null ? null : source.mergedByName(),
                source == null ? null : source.mergedAt(), version.getRollbackFromVersion(),
                version.getContentSha256(), source != null && Boolean.TRUE.equals(source.executionAvailable()),
                version.getCreatedAt());
    }

    private Map<Long, UserRefVO> toUserMap(List<UserRefVO> users) {
        if (users == null || users.isEmpty()) return Map.of();
        return users.stream().filter(Objects::nonNull).filter(user -> user.id() != null)
                .collect(Collectors.toMap(UserRefVO::id, Function.identity(), (left, right) -> left));
    }

    private DocumentVersionSourceType sourceType(DocumentVersionEntity version) {
        try {
            return version.getSourceType() == null ? DocumentVersionSourceType.UNKNOWN
                    : DocumentVersionSourceType.valueOf(version.getSourceType());
        } catch (IllegalArgumentException ignored) {
            return DocumentVersionSourceType.UNKNOWN;
        }
    }

    private DocumentVersionActorType actorType(DocumentVersionEntity version) {
        try {
            return version.getActorType() == null ? DocumentVersionActorType.UNKNOWN
                    : DocumentVersionActorType.valueOf(version.getActorType());
        } catch (IllegalArgumentException ignored) {
            return DocumentVersionActorType.UNKNOWN;
        }
    }

    /**
     * 读权限校验：校验文档存在，且当前登录用户是该文档所属空间的成员
     * 说明：版本是文档的子资源，所有版本查询接口必须先过此校验
     *
     * @param documentId 文档ID
     */
    private DocumentEntity checkReadable(Long documentId) {
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        permissionService.requirePermission(doc.getSpaceId(), DOCUMENT_READ);
        return doc;
    }

    /**
     * 根据文档ID + 版本号获取版本实体，不存在抛出404业务异常
     *
     * @param documentId 文档ID
     * @param versionNo 版本号
     * @return 文档版本数据库实体
     */
    public DocumentVersionEntity requireVersion(Long documentId, Long versionNo) {
        DocumentVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, documentId)
                .eq(DocumentVersionEntity::getVersionNo, versionNo));
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        return version;
    }

    private String sha256(String content) {
        try {
            byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }

    private record VersionContext(Map<Long, DocumentVersionSourceVO> byChangeRequest,
                                  Map<Long, DocumentVersionSourceVO> byTask,
                                  Map<Long, UserRefVO> users) {
        private DocumentVersionSourceVO source(DocumentVersionEntity version) {
            DocumentVersionSourceVO source = version.getSourceChangeRequestId() == null
                    ? null : byChangeRequest.get(version.getSourceChangeRequestId());
            return source != null || version.getSourceTaskId() == null
                    ? source : byTask.get(version.getSourceTaskId());
        }

        private String actorName(DocumentVersionEntity version) {
            if (DocumentVersionActorType.HUMAN.name().equals(version.getActorType())) {
                UserRefVO user = version.getActorId() == null ? null : users.get(version.getActorId());
                return user == null ? null
                        : (user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname());
            }
            if (DocumentVersionActorType.AGENT.name().equals(version.getActorType())) {
                DocumentVersionSourceVO source = source(version);
                return source == null ? null : source.agentName();
            }
            return null;
        }
    }
}
