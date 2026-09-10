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

    /**
     * 分页查询变更请求列表
     * 支持按空间、文档ID、状态、时间范围、Agent、分配给我等条件过滤；
     * 批量拉取文档、任务、Agent、评审人信息组装列表VO
     * @param param 变更请求搜索参数
     * @return 分页变更请求列表VO
     */
    public PageVO<ChangeRequestListItemVO> list(ChangeRequestSearchParam param) {
        // 校验空间变更请求读取权限
        requirePermission(param.spaceId());
        // 获取分页参数，为空则创建默认分页对象
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

        // 过滤指定Agent发起的变更请求
        if (param.agentId() != null) {
            wrapper.eq(ChangeRequestEntity::getProposedActorType, ActorType.AGENT.getCode())
                    .eq(ChangeRequestEntity::getProposedBy, param.agentId());
        }

        // 筛选分配给当前登录用户的变更请求
        if (Boolean.TRUE.equals(param.assignedToMe())) {
            wrapper.eq(ChangeRequestEntity::getAssignedReviewerId, AuthUtils.getUserIdOrException());
        }

        // 执行分页查询
        Page<ChangeRequestEntity> page = changeRequestMapper.selectPage(PageUtils.toPage(pageParam), wrapper);
        return PageVO.of(toListItems(page.getRecords()), page.getTotal(), pageParam);
    }

    /**
     * 获取变更请求详情
     * 包含变更预览、关联任务、Agent信息、Token用量、评论、审计轨迹、用户名称等全部信息
     * @param id 变更请求ID
     * @return 变更请求完整详情VO
     */
    public ChangeRequestDetailVO detail(Long id) {
        // 查询变更请求实体，不存在抛异常
        ChangeRequestEntity entity = requireRequest(id);
        // 校验空间读取权限
        requirePermission(entity.getSpaceId());

        // 解析变更请求内的变更集合
        var changes = ChangeRequestConvertor.parseChanges(entity.getChanges());
        // 调用文档服务获取变更预览（基准版本、提议内容、冲突标记）
        DocumentChangePreviewVO preview = requireData(documentFeign.previewDocumentChanges(
                new DocumentChangePreviewRequestDTO(entity.getDocumentId(), entity.getBaseVersion(), changes)));

        // 查询关联的源任务
        TaskEntity task = entity.getSourceTaskId() == null ? null : taskMapper.selectById(entity.getSourceTaskId());

        // 获取AgentId，优先取任务，没有则取变更请求本身的提议人
        Long agentId = agentId(entity, task);
        // 批量查询Agent信息
        Map<Long, AgentRefVO> agents = fetchAgents(agentId == null ? List.of() : List.of(agentId));
        // 获取Agent执行审计，用于兜底token用量
        AgentExecutionAuditVO execution = fetchExecutionAudit(task, entity.getSpaceId());

        // 优先使用任务自带token用量；任务无数据则从执行审计计算
        Long tokensUsed = task == null ? null : task.getTokensUsed();
        Boolean tokensEstimated = task == null ? null : task.getTokensEstimated();
        if (tokensUsed == null && execution != null
                && execution.inputTokens() != null && execution.outputTokens() != null) {
            tokensUsed = execution.inputTokens() + execution.outputTokens();
            tokensEstimated = Boolean.TRUE.equals(tokensEstimated)
                    || Boolean.TRUE.equals(execution.inputTokensEstimated())
                    || Boolean.TRUE.equals(execution.outputTokensEstimated());
        }

        // 查询变更请求下全部评论，按创建时间正序
        List<ChangeRequestCommentEntity> comments = commentMapper.selectList(
                new LambdaQueryWrapper<ChangeRequestCommentEntity>()
                        .eq(ChangeRequestCommentEntity::getChangeRequestId, id)
                        .orderByAsc(ChangeRequestCommentEntity::getCreatedAt));

        // 查询变更请求审计轨迹
        List<AuditLogEntity> audit = auditLogService.listChangeRequestTrail(id);

        // 获取触发人ID
        Long triggeredBy = triggeredBy(entity, task);

        // 收集所有需要查询的用户ID（发起人、评审人、合并人、评论作者、审计日志人工操作者）
        Set<Long> userIds = collectUserIds(entity, task, comments, audit);
        // 批量拉取用户信息
        Map<Long, UserRefVO> users = fetchUsers(new ArrayList<>(userIds));

        // 组装展示名称
        String agentName = displayName(getOrNull(agents, agentId));
        ActorType proposedActorType = ActorType.fromCode(entity.getProposedActorType());
        String proposedByName = proposedActorType == ActorType.AGENT
                ? agentName : displayName(getOrNull(users, entity.getProposedBy()));
        String triggeredByName = displayName(getOrNull(users, triggeredBy));

        // 组装完整详情VO返回
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

    /**
     * 获取变更请求统计指标
     * 统计：全部待处理、历史待处理、分配给我、未分配的变更请求数量
     * @param spaceId 空间ID
     * @return 待处理变更统计VO
     */
    public PendingChangeStatsVO getStats(Long spaceId) {
        requirePermission(spaceId);
        // 全部待处理数量
        long pending = count(spaceId, null, null);
        // 历史待处理：昨天及之前创建的待处理
        long historical = changeRequestMapper.selectCount(new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSpaceId, spaceId)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .lt(ChangeRequestEntity::getCreatedAt, LocalDate.now().atStartOfDay()));
        // 分配给当前登录用户的待处理
        long mine = count(spaceId, AuthUtils.getUserIdOrException(), Boolean.FALSE);
        // 未分配评审人的待处理
        long unassigned = count(spaceId, null, Boolean.TRUE);
        return new PendingChangeStatsVO(pending, historical, mine, unassigned);
    }

    /**
     * 变更请求计数工具
     * @param spaceId 空间ID
     * @param reviewerId 指定评审人ID；null则不筛选评审人
     * @param unassigned 是否只查询未分配评审人；true=只查null的assignedReviewerId
     * @return 匹配条件的待处理变更请求数量
     */
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

    /**
     * 批量将变更请求实体转为列表VO
     * 批量拉取文档、任务、Agent、评审人，避免循环feign调用
     * @param entities 变更请求实体集合
     * @return 变更请求列表VO集合
     */
    private List<ChangeRequestListItemVO> toListItems(List<ChangeRequestEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        // 批量获取文档引用信息
        Map<Long, DocumentRefVO> documents = fetchDocuments(entities.stream()
                .map(ChangeRequestEntity::getDocumentId).distinct().toList());
        // 批量获取关联任务
        Map<Long, TaskEntity> tasks = fetchTasks(entities.stream()
                .map(ChangeRequestEntity::getSourceTaskId).filter(Objects::nonNull).distinct().toList());
        // 批量获取Agent信息
        Map<Long, AgentRefVO> agents = fetchAgents(entities.stream()
                .map(entity -> agentId(entity, getOrNull(tasks, entity.getSourceTaskId())))
                .filter(Objects::nonNull).distinct().toList());
        // 批量获取评审人用户信息
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

    /**
     * 收集详情页需要查询的全部用户ID
     * 包含：触发人、评审人、审核人、合并人、评论作者、审计日志人工操作者
     * @param entity 变更请求
     * @param task 关联任务
     * @param comments 变更请求评论
     * @param audit 审计日志
     * @return 去重用户ID集合
     */
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

    /**
     * 获取变更请求触发人ID
     * 优先取任务创建人；任务不存在则取人工类型的提议人
     * @param entity 变更请求
     * @param task 关联任务
     * @return 触发人ID，Agent发起则返回null
     */
    private Long triggeredBy(ChangeRequestEntity entity, TaskEntity task) {
        if (task != null && task.getCreatedBy() != null) return task.getCreatedBy();
        return Objects.equals(entity.getProposedActorType(), ActorType.HUMAN.getCode())
                ? entity.getProposedBy() : null;
    }

    /**
     * 评论实体转换为评论VO
     * @param comments 评论实体列表
     * @param users 用户ID-名称映射
     * @return 评论VO列表
     */
    private List<ChangeRequestCommentVO> toCommentVOs(List<ChangeRequestCommentEntity> comments,
                                                      Map<Long, UserRefVO> users) {
        return comments.stream().map(comment -> new ChangeRequestCommentVO(
                comment.getId(), comment.getChangeKey(), comment.getAuthorId(),
                displayName(getOrNull(users, comment.getAuthorId())), comment.getContent(), comment.getCreatedAt())).toList();
    }

    /**
     * 审计日志实体转换为变更请求审计VO
     * @param rows 审计日志实体
     * @param users 用户映射
     * @param agents Agent映射
     * @return 审计VO列表
     */
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

    /**
     * 批量查询文档引用信息
     * @param ids 文档ID集合
     * @return 文档ID -> DocumentRefVO映射
     */
    private Map<Long, DocumentRefVO> fetchDocuments(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<DocumentRefVO> rows = requireData(documentFeign.getDocumentRefs(ids));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(DocumentRefVO::id, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量查询任务实体
     * @param ids 任务ID集合
     * @return 任务ID -> TaskEntity映射
     */
    private Map<Long, TaskEntity> fetchTasks(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<TaskEntity> rows = taskMapper.selectBatchIds(ids);
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getId() != null)
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量远程查询Agent引用信息
     * @param ids AgentID集合
     * @return AgentID -> AgentRefVO映射
     */
    private Map<Long, AgentRefVO> fetchAgents(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<AgentRefVO> rows = requireData(agentFeign.queryAgentRefs(new AgentBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(AgentRefVO::id, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量远程查询用户引用信息
     * @param ids 用户ID集合
     * @return 用户ID -> UserRefVO映射
     */
    private Map<Long, UserRefVO> fetchUsers(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UserRefVO> rows = requireData(authFeign.queryUsers(new UserBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.id() != null)
                .collect(Collectors.toMap(UserRefVO::id, Function.identity(), (left, right) -> left));
    }

    /**
     * 获取Agent执行审计信息
     * @param task 任务实体
     * @param spaceId 空间ID
     * @return Agent执行审计VO，失败返回null
     */
    private AgentExecutionAuditVO fetchExecutionAudit(TaskEntity task, Long spaceId) {
        if (task == null || task.getId() == null) return null;
        Result<AgentExecutionAuditVO> result = agentFeign.getExecutionAudit(task.getId(), spaceId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) return null;
        return result.data();
    }

    /**
     * 查询变更请求，不存在抛出404业务异常
     * @param id 变更请求ID
     * @return 变更请求实体
     */
    private ChangeRequestEntity requireRequest(Long id) {
        ChangeRequestEntity entity = changeRequestMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "变更请求不存在");
        }
        return entity;
    }

    /**
     * 获取AgentId
     * 优先取关联任务的agentId；任务不存在且提议人为Agent，则取变更请求proposedBy
     * @param entity 变更请求
     * @param task 关联任务
     * @return AgentId，无则null
     */
    private Long agentId(ChangeRequestEntity entity, TaskEntity task) {
        if (task != null) return task.getAgentId();
        return Objects.equals(entity.getProposedActorType(), ActorType.AGENT.getCode())
                ? entity.getProposedBy() : null;
    }

    /**
     * 字符串解析为变更决议类型枚举
     * @param value 存储字符串
     * @return 枚举对象，null返回null
     */
    private ChangeRequestResolutionType resolutionType(String value) {
        return value == null ? null : ChangeRequestResolutionType.valueOf(value);
    }

    /**
     * json字符串解析为字符串列表
     * @param json json字符串
     * @return 字符串列表，null返回空集合
     */
    private List<String> parseStringList(String json) {
        List<String> values = JsonUtils.parse(json, new TypeReference<List<String>>() { });
        return values == null ? List.of() : values;
    }

    /**
     * 获取用户展示名称，优先昵称，无则用户名
     * @param user 用户引用对象
     * @return 展示名称，null返回null
     */
    private String displayName(UserRefVO user) {
        if (user == null) return null;
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    /**
     * 获取Agent展示名称
     * @param agent Agent引用对象
     * @return Agent名称，null返回null
     */
    private String displayName(AgentRefVO agent) {
        return agent == null ? null : agent.name();
    }

    /**
     * Map安全获取值，id为null直接返回null，避免NPE
     * @param values 映射表
     * @param id key
     * @return 对应value，null
     * @param <T> 值类型
     */
    private <T> T getOrNull(Map<Long, T> values, Long id) {
        return id == null ? null : values.get(id);
    }

    /**
     * 获取修订版本号，null默认返回1
     * @param entity 变更请求
     * @return 修订号
     */
    private Integer revisionNo(ChangeRequestEntity entity) {
        return entity.getRevisionNo() == null ? 1 : entity.getRevisionNo();
    }

    /**
     * Set安全添加ID，id不为null才加入集合
     * @param ids 目标集合
     * @param id 待添加ID
     */
    private void add(Set<Long> ids, Long id) {
        if (id != null) ids.add(id);
    }

    /**
     * 校验空间变更请求读取权限
     * @param spaceId 空间ID
     */
    private void requirePermission(Long spaceId) {
        requireData(documentFeign.checkSpacePermission(spaceId, CHANGE_REQUEST_READ));
    }

    /**
     * Feign远程调用结果断言工具
     * 校验Result成功，失败抛业务异常；成功返回data
     * @param result feign返回结果
     * @return 响应数据
     * @param <T> 返回数据泛型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}