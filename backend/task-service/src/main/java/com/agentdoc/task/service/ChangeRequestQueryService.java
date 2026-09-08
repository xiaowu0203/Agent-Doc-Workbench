package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.DocumentChangePreviewRequestDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.DocumentChangePreviewVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.PageUtils;
import com.agentdoc.task.convertor.ChangeRequestConvertor;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.ChangeRequestResolutionType;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import com.agentdoc.task.mapper.ChangeRequestCommentMapper;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.AuditLogEntity;
import com.agentdoc.task.pojo.entity.ChangeRequestCommentEntity;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.param.ChangeRequestSearchParam;
import com.agentdoc.task.pojo.vo.ChangeRequestAuditVO;
import com.agentdoc.task.pojo.vo.ChangeRequestCommentVO;
import com.agentdoc.task.pojo.vo.ChangeRequestDetailVO;
import com.agentdoc.task.pojo.vo.ChangeRequestListItemVO;
import com.agentdoc.task.pojo.vo.PendingChangeStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;

/** 审批队列和详情的只读聚合服务。 */
@Service
@RequiredArgsConstructor
public class ChangeRequestQueryService {

    private final ChangeRequestMapper changeRequestMapper;
    private final ChangeRequestCommentMapper commentMapper;
    private final TaskMapper taskMapper;
    private final DocumentFeign documentFeign;
    private final AgentFeign agentFeign;
    private final AuthFeign authFeign;
    private final AuditLogService auditLogService;

    public PageVO<ChangeRequestListItemVO> list(ChangeRequestSearchParam param) {
        requirePermission(param.spaceId());
        PageParam pageParam = param.pageParam() == null ? new PageParam() : param.pageParam();
        pageParam.validate();
        LambdaQueryWrapper<ChangeRequestEntity> wrapper = new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSpaceId, param.spaceId())
                .eq(param.documentId() != null, ChangeRequestEntity::getDocumentId, param.documentId())
                .eq(param.status() != null, ChangeRequestEntity::getStatus,
                        param.status() == null ? null : param.status().getCode())
                .ge(param.createdFrom() != null, ChangeRequestEntity::getCreatedAt, param.createdFrom())
                .le(param.createdTo() != null, ChangeRequestEntity::getCreatedAt, param.createdTo())
                .orderByDesc(ChangeRequestEntity::getCreatedAt);
        if (param.agentId() != null) {
            wrapper.eq(ChangeRequestEntity::getProposedActorType, ActorType.AGENT.getCode())
                    .eq(ChangeRequestEntity::getProposedBy, param.agentId());
        }
        if (Boolean.TRUE.equals(param.assignedToMe())) {
            wrapper.eq(ChangeRequestEntity::getAssignedReviewerId, AuthUtils.getUserIdOrException());
        }
        Page<ChangeRequestEntity> page = changeRequestMapper.selectPage(PageUtils.toPage(pageParam), wrapper);
        return PageVO.of(toListItems(page.getRecords()), page.getTotal(), pageParam);
    }

    public ChangeRequestDetailVO detail(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId());
        var changes = ChangeRequestConvertor.parseChanges(entity.getChanges());
        DocumentChangePreviewVO preview = requireData(documentFeign.previewDocumentChanges(
                new DocumentChangePreviewRequestDTO(entity.getDocumentId(), entity.getBaseVersion(), changes)));
        TaskEntity task = entity.getSourceTaskId() == null ? null : taskMapper.selectById(entity.getSourceTaskId());
        Long agentId = agentId(entity, task);
        Map<Long, AgentRefVO> agents = fetchAgents(agentId == null ? List.of() : List.of(agentId));
        AgentExecutionAuditVO execution = fetchExecutionAudit(task, entity.getSpaceId());
        Long tokensUsed = task == null ? null : task.getTokensUsed();
        Boolean tokensEstimated = task == null ? null : task.getTokensEstimated();
        if (tokensUsed == null && execution != null
                && execution.inputTokens() != null && execution.outputTokens() != null) {
            tokensUsed = execution.inputTokens() + execution.outputTokens();
            tokensEstimated = Boolean.TRUE.equals(tokensEstimated)
                    || Boolean.TRUE.equals(execution.inputTokensEstimated())
                    || Boolean.TRUE.equals(execution.outputTokensEstimated());
        }
        List<ChangeRequestCommentEntity> comments = commentMapper.selectList(
                new LambdaQueryWrapper<ChangeRequestCommentEntity>()
                        .eq(ChangeRequestCommentEntity::getChangeRequestId, id)
                        .orderByAsc(ChangeRequestCommentEntity::getCreatedAt));
        List<AuditLogEntity> audit = auditLogService.listChangeRequestTrail(id);
        Long triggeredBy = triggeredBy(entity, task);
        Set<Long> userIds = collectUserIds(entity, task, comments, audit);
        Map<Long, UserRefVO> users = fetchUsers(new ArrayList<>(userIds));
        String agentName = displayName(getOrNull(agents, agentId));
        ActorType proposedActorType = ActorType.fromCode(entity.getProposedActorType());
        String proposedByName = proposedActorType == ActorType.AGENT
                ? agentName : displayName(getOrNull(users, entity.getProposedBy()));
        String triggeredByName = displayName(getOrNull(users, triggeredBy));

        return new ChangeRequestDetailVO(
                entity.getId(), entity.getSpaceId(), entity.getDocumentId(), preview.documentTitle(),
                ChangeRequestType.fromCode(entity.getRequestType()), ChangeRequestStatus.fromCode(entity.getStatus()),
                entity.getSummary(), changes, entity.getBaseVersion(), preview.baseVersionCreatedAt(),
                preview.currentVersion(), preview.expectedVersion(), preview.baseContent(), preview.proposedContent(),
                entity.getResolvedContent(), preview.conflicted(), entity.getSourceTaskId(),
                task == null ? null : task.getTaskNo(), task == null ? null : task.getName(), agentId, agentName,
                tokensUsed, tokensEstimated,
                task == null ? null : task.getResultSummary(), entity.getProposedBy(), proposedActorType,
                proposedByName, triggeredBy, triggeredByName, entity.getAssignedReviewerId(),
                displayName(getOrNull(users, entity.getAssignedReviewerId())),
                entity.getReviewComment(), entity.getReviewedBy(), displayName(getOrNull(users, entity.getReviewedBy())),
                entity.getReviewedAt(), resolutionType(entity.getResolutionType()),
                parseStringList(entity.getAcceptedChangeKeys()), entity.getMergedBy(),
                displayName(getOrNull(users, entity.getMergedBy())), entity.getMergedAt(), entity.getMergedVersion(),
                entity.getParentRequestId(), revisionNo(entity), entity.getReworkTaskId(),
                toCommentVOs(comments, users), toAuditVOs(audit, users, agents),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public PendingChangeStatsVO getStats(Long spaceId) {
        requirePermission(spaceId);
        long pending = count(spaceId, null, null);
        long historical = changeRequestMapper.selectCount(new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSpaceId, spaceId)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .lt(ChangeRequestEntity::getCreatedAt, LocalDate.now().atStartOfDay()));
        long mine = count(spaceId, AuthUtils.getUserIdOrException(), Boolean.FALSE);
        long unassigned = count(spaceId, null, Boolean.TRUE);
        return new PendingChangeStatsVO(pending, historical, mine, unassigned);
    }

    private long count(Long spaceId, Long reviewerId, Boolean unassigned) {
        LambdaQueryWrapper<ChangeRequestEntity> wrapper = new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSpaceId, spaceId)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode());
        if (reviewerId != null) {
            wrapper.eq(ChangeRequestEntity::getAssignedReviewerId, reviewerId);
        } else if (Boolean.TRUE.equals(unassigned)) {
            wrapper.isNull(ChangeRequestEntity::getAssignedReviewerId);
        }
        return changeRequestMapper.selectCount(wrapper);
    }

    private List<ChangeRequestListItemVO> toListItems(List<ChangeRequestEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Map<Long, DocumentRefVO> documents = fetchDocuments(entities.stream()
                .map(ChangeRequestEntity::getDocumentId).distinct().toList());
        Map<Long, TaskEntity> tasks = fetchTasks(entities.stream()
                .map(ChangeRequestEntity::getSourceTaskId).filter(Objects::nonNull).distinct().toList());
        Map<Long, AgentRefVO> agents = fetchAgents(entities.stream()
                .map(entity -> agentId(entity, getOrNull(tasks, entity.getSourceTaskId())))
                .filter(Objects::nonNull).distinct().toList());
        Map<Long, UserRefVO> reviewers = fetchUsers(entities.stream()
                .map(ChangeRequestEntity::getAssignedReviewerId).filter(Objects::nonNull).distinct().toList());
        return entities.stream().map(entity -> {
            TaskEntity task = getOrNull(tasks, entity.getSourceTaskId());
            Long currentAgentId = agentId(entity, task);
            DocumentRefVO document = getOrNull(documents, entity.getDocumentId());
            return new ChangeRequestListItemVO(entity.getId(), entity.getDocumentId(),
                    document == null ? null : document.title(), ChangeRequestStatus.fromCode(entity.getStatus()),
                    entity.getSummary(), entity.getSourceTaskId(), task == null ? null : task.getTaskNo(),
                    task == null ? null : task.getName(), currentAgentId,
                    displayName(getOrNull(agents, currentAgentId)), entity.getAssignedReviewerId(),
                    displayName(getOrNull(reviewers, entity.getAssignedReviewerId())), revisionNo(entity),
                    entity.getCreatedAt(), entity.getUpdatedAt());
        }).toList();
    }

    private Set<Long> collectUserIds(ChangeRequestEntity entity, TaskEntity task,
                                     List<ChangeRequestCommentEntity> comments,
                                     List<AuditLogEntity> audit) {
        Set<Long> ids = new LinkedHashSet<>();
        add(ids, triggeredBy(entity, task));
        add(ids, entity.getAssignedReviewerId());
        add(ids, entity.getReviewedBy());
        add(ids, entity.getMergedBy());
        comments.forEach(comment -> add(ids, comment.getAuthorId()));
        audit.stream().filter(row -> Objects.equals(row.getActorType(), ActorType.HUMAN.getCode()))
                .forEach(row -> add(ids, row.getActorId()));
        return ids;
    }

    private Long triggeredBy(ChangeRequestEntity entity, TaskEntity task) {
        if (task != null && task.getCreatedBy() != null) return task.getCreatedBy();
        return Objects.equals(entity.getProposedActorType(), ActorType.HUMAN.getCode())
                ? entity.getProposedBy() : null;
    }

    private List<ChangeRequestCommentVO> toCommentVOs(List<ChangeRequestCommentEntity> comments,
                                                       Map<Long, UserRefVO> users) {
        return comments.stream().map(comment -> new ChangeRequestCommentVO(
                comment.getId(), comment.getChangeKey(), comment.getAuthorId(),
                displayName(getOrNull(users, comment.getAuthorId())), comment.getContent(), comment.getCreatedAt())).toList();
    }

    private List<ChangeRequestAuditVO> toAuditVOs(List<AuditLogEntity> rows, Map<Long, UserRefVO> users,
                                                  Map<Long, AgentRefVO> agents) {
        return rows.stream().map(row -> {
            ActorType actorType = ActorType.fromCode(row.getActorType());
            String actorName = actorType == ActorType.AGENT
                    ? displayName(getOrNull(agents, row.getActorId())) : displayName(getOrNull(users, row.getActorId()));
            return new ChangeRequestAuditVO(row.getId(), actorType, row.getActorId(), actorName,
                    AuditAction.valueOf(row.getAction()), row.getDetail(), row.getTraceId(), row.getCreatedAt());
        }).toList();
    }

    private Map<Long, DocumentRefVO> fetchDocuments(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<DocumentRefVO> rows = requireData(documentFeign.getDocumentRefs(ids));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(DocumentRefVO::id, Function.identity(), (left, right) -> left));
    }

    private Map<Long, TaskEntity> fetchTasks(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<TaskEntity> rows = taskMapper.selectBatchIds(ids);
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, AgentRefVO> fetchAgents(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<AgentRefVO> rows = requireData(agentFeign.queryAgentRefs(new AgentBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(AgentRefVO::id, Function.identity(), (left, right) -> left));
    }

    private Map<Long, UserRefVO> fetchUsers(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UserRefVO> rows = requireData(authFeign.queryUsers(new UserBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(UserRefVO::id, Function.identity(), (left, right) -> left));
    }

    private AgentExecutionAuditVO fetchExecutionAudit(TaskEntity task, Long spaceId) {
        if (task == null || task.getId() == null) return null;
        Result<AgentExecutionAuditVO> result = agentFeign.getExecutionAudit(task.getId(), spaceId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) return null;
        return result.data();
    }

    private ChangeRequestEntity requireRequest(Long id) {
        ChangeRequestEntity entity = changeRequestMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "变更请求不存在");
        }
        return entity;
    }

    private Long agentId(ChangeRequestEntity entity, TaskEntity task) {
        if (task != null) return task.getAgentId();
        return Objects.equals(entity.getProposedActorType(), ActorType.AGENT.getCode())
                ? entity.getProposedBy() : null;
    }

    private ChangeRequestResolutionType resolutionType(String value) {
        return value == null ? null : ChangeRequestResolutionType.valueOf(value);
    }

    private List<String> parseStringList(String json) {
        List<String> values = JsonUtils.parse(json, new TypeReference<List<String>>() { });
        return values == null ? List.of() : values;
    }

    private String displayName(UserRefVO user) {
        if (user == null) return null;
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    private String displayName(AgentRefVO agent) {
        return agent == null ? null : agent.name();
    }

    private <T> T getOrNull(Map<Long, T> values, Long id) {
        return id == null ? null : values.get(id);
    }

    private Integer revisionNo(ChangeRequestEntity entity) {
        return entity.getRevisionNo() == null ? 1 : entity.getRevisionNo();
    }

    private void add(Set<Long> ids, Long id) {
        if (id != null) ids.add(id);
    }

    private void requirePermission(Long spaceId) {
        requireData(documentFeign.checkSpacePermission(spaceId, CHANGE_REQUEST_READ));
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}
