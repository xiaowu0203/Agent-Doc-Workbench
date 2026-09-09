package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentExecutionTokenUsageBatchQueryDTO;
import com.agentdoc.common.feign.dto.DocumentVersionSourceQueryDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageBatchVO;
import com.agentdoc.common.feign.vo.DocumentVersionSourceVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;

/**
 * 批量聚合文档版本关联的任务、Agent 与审批信息。
 * <p>根据传入的变更请求ID、任务ID，聚合对应的任务、Agent执行用量、审批人、合并人信息，
 * 用于展示文档版本的来源溯源；支持变更请求关联的源任务自动补全查询。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentVersionSourceQueryService {

    /** 单次查询来源ID最大数量限制，防止批量查询过大 */
    private static final int MAX_SOURCE_IDS = 100;

    private final ChangeRequestMapper changeRequestMapper;
    private final TaskMapper taskMapper;
    private final DocumentFeign documentFeign;
    private final AgentFeign agentFeign;
    private final AuthFeign authFeign;

    /**
     * 查询文档版本来源信息
     * <p>执行流程：参数校验 → 空间读权限校验 → 查询变更请求 → 收集关联任务ID → 查询任务 →
     * 批量拉取Agent令牌用量、Agent基础信息、用户信息 → 组装VO，变更请求优先展示，再展示未被变更请求关联的独立任务</p>
     * @param request 查询入参：空间ID、变更请求ID列表、任务ID列表
     * @return 聚合后的版本来源VO列表
     */
    public List<DocumentVersionSourceVO> query(DocumentVersionSourceQueryDTO request) {
        // 参数合法性校验
        validate(request);
        // 校验空间文档读取权限
        requireData(documentFeign.checkSpacePermission(request.spaceId(), DOCUMENT_READ));

        // 清洗变更请求ID，过滤null、去重
        List<Long> changeRequestIds = distinct(request.changeRequestIds());
        // 查询指定空间下的变更请求
        List<ChangeRequestEntity> requests = changeRequestIds.isEmpty() ? List.of()
                : changeRequestMapper.selectList(new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSpaceId, request.spaceId())
                .in(ChangeRequestEntity::getId, changeRequestIds));

        // 收集任务ID：入参传入的任务ID + 变更请求关联的源任务ID
        Set<Long> taskIds = new LinkedHashSet<>(distinct(request.taskIds()));
        requests.stream().map(ChangeRequestEntity::getSourceTaskId)
                .filter(Objects::nonNull).forEach(taskIds::add);

        // 查询任务实体，转为id->task的map方便快速查找
        Map<Long, TaskEntity> tasks = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getSpaceId, request.spaceId())
                        .in(TaskEntity::getId, taskIds)).stream()
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity()));

        // 批量查询Agent执行令牌用量数据
        Map<Long, AgentExecutionTokenUsageBatchVO> executionTokens = fetchExecutionTokens(tasks.values());
        // 批量查询Agent基础信息
        Map<Long, AgentRefVO> agents = fetchAgents(tasks.values().stream()
                .map(TaskEntity::getAgentId).filter(Objects::nonNull).distinct().toList());

        // 收集所有需要查询的用户ID：任务创建人、变更请求审批人、变更请求合并人
        Set<Long> userIds = new LinkedHashSet<>();
        tasks.values().stream().map(TaskEntity::getCreatedBy).filter(Objects::nonNull).forEach(userIds::add);
        requests.stream().map(ChangeRequestEntity::getReviewedBy).filter(Objects::nonNull).forEach(userIds::add);
        requests.stream().map(ChangeRequestEntity::getMergedBy).filter(Objects::nonNull).forEach(userIds::add);
        // 批量查询用户信息
        Map<Long, UserRefVO> users = fetchUsers(new ArrayList<>(userIds));

        List<DocumentVersionSourceVO> result = new ArrayList<>();
        // 记录已经被变更请求引用过的任务ID，避免重复输出
        Set<Long> representedTaskIds = new LinkedHashSet<>();

        // 第一步：处理变更请求，关联对应的源任务，加入结果集
        for (ChangeRequestEntity changeRequest : requests) {
            TaskEntity task = getOrNull(tasks, changeRequest.getSourceTaskId());
            if (task != null) representedTaskIds.add(task.getId());
            result.add(toVO(changeRequest, task, agents, users, executionTokens));
        }

        // 第二步：追加没有被变更请求关联的独立任务
        tasks.values().stream()
                .filter(task -> !representedTaskIds.contains(task.getId()))
                .map(task -> toVO(null, task, agents, users, executionTokens))
                .forEach(result::add);

        return result;
    }

    /**
     * 实体转VO，组装版本来源视图对象
     * <p>令牌用量逻辑：优先使用任务表tokensUsed；如果为空，则从Agent执行记录累加input+output令牌；
     * 同时标记令牌是否为估算值。</p>
     * @param request 变更请求实体，可为null
     * @param task 任务实体，可为null
     * @param agents Agent信息map
     * @param users 用户信息map
     * @param executionTokens Agent执行令牌用量
     * @return 文档版本来源VO
     */
    private DocumentVersionSourceVO toVO(ChangeRequestEntity request, TaskEntity task,
                                         Map<Long, AgentRefVO> agents, Map<Long, UserRefVO> users,
                                         Map<Long, AgentExecutionTokenUsageBatchVO> executionTokens) {
        Long agentId = task == null ? null : task.getAgentId();
        Long triggeredBy = task == null ? null : task.getCreatedBy();
        Long reviewedBy = request == null ? null : request.getReviewedBy();
        Long mergedBy = request == null ? null : request.getMergedBy();

        AgentExecutionTokenUsageBatchVO execution = task == null ? null : executionTokens.get(task.getId());
        Long tokensUsed = task == null ? null : task.getTokensUsed();
        Boolean tokensEstimated = task == null ? null : task.getTokensEstimated();

        // 任务表令牌为空时，使用Agent执行记录的输入+输出令牌作为总用量
        if (tokensUsed == null && execution != null
                && execution.inputTokens() != null && execution.outputTokens() != null) {
            tokensUsed = execution.inputTokens() + execution.outputTokens();
            // 只要有任意一处是估算，则标记为估算令牌
            tokensEstimated = Boolean.TRUE.equals(tokensEstimated)
                    || Boolean.TRUE.equals(execution.inputTokensEstimated())
                    || Boolean.TRUE.equals(execution.outputTokensEstimated());
        }

        return new DocumentVersionSourceVO(
                request == null ? null : request.getId(), task == null ? null : task.getId(),
                task == null ? null : task.getTaskNo(), task == null ? null : task.getName(),
                agentId, agentName(getOrNull(agents, agentId)), triggeredBy, userName(getOrNull(users, triggeredBy)),
                tokensUsed, tokensEstimated,
                reviewedBy, userName(getOrNull(users, reviewedBy)), request == null ? null : request.getReviewedAt(),
                mergedBy, userName(getOrNull(users, mergedBy)), request == null ? null : request.getMergedAt(),
                task != null && (task.getAgentExecutionId() != null || execution != null));
    }

    /**
     * 批量查询Agent执行令牌用量
     * @param tasks 任务集合
     * @return taskId -> 令牌用量VO
     */
    private Map<Long, AgentExecutionTokenUsageBatchVO> fetchExecutionTokens(
            Collection<TaskEntity> tasks) {
        List<Long> taskIds = tasks.stream().map(TaskEntity::getId).filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) return Map.of();
        List<AgentExecutionTokenUsageBatchVO> rows = requireData(agentFeign.queryExecutionTokenUsages(
                new AgentExecutionTokenUsageBatchQueryDTO(taskIds)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().filter(Objects::nonNull).filter(row -> row.taskId() != null)
                .collect(Collectors.toMap(AgentExecutionTokenUsageBatchVO::taskId, Function.identity(),
                        (left, right) -> left));
    }

    /**
     * 批量查询Agent引用信息
     * @param ids AgentId列表
     * @return agentId -> AgentRefVO
     */
    private Map<Long, AgentRefVO> fetchAgents(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<AgentRefVO> rows = requireData(agentFeign.queryAgentRefs(new AgentBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().filter(Objects::nonNull).filter(row -> row.id() != null)
                .collect(Collectors.toMap(AgentRefVO::id, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量查询用户引用信息
     * @param ids 用户ID列表
     * @return userId -> UserRefVO
     */
    private Map<Long, UserRefVO> fetchUsers(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UserRefVO> rows = requireData(authFeign.queryUsers(new UserBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().filter(Objects::nonNull).filter(row -> row.id() != null)
                .collect(Collectors.toMap(UserRefVO::id, Function.identity(), (left, right) -> left));
    }

    /**
     * 获取Agent名称，null返回null
     * @param agent Agent引用对象
     * @return Agent名称
     */
    private String agentName(AgentRefVO agent) {
        return agent == null ? null : agent.name();
    }

    /**
     * 获取用户展示名：优先昵称，无昵称则使用用户名
     * @param user 用户引用对象
     * @return 用户展示名称
     */
    private String userName(UserRefVO user) {
        if (user == null) return null;
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    /**
     * Map安全获取值，id为null直接返回null，避免NPE
     * @param values 目标map
     * @param id key
     * @return map对应value，不存在返回null
     * @param <T> 值类型
     */
    private <T> T getOrNull(Map<Long, T> values, Long id) {
        return id == null ? null : values.get(id);
    }

    /**
     * 清洗ID列表：过滤null，去重；入参null返回空列表
     * @param ids 原始id列表
     * @return 清洗后的id列表
     */
    private List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * 参数校验
     * <ul>
     * <li>必须传入空间ID</li>
     * <li>变更请求、任务ID各自不能超过最大查询上限 MAX_SOURCE_IDS(100)</li>
     * </ul>
     * @param request 查询入参
     */
    private void validate(DocumentVersionSourceQueryDTO request) {
        if (request == null || request.spaceId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "版本来源查询缺少空间 ID");
        }
        if (distinct(request.changeRequestIds()).size() > MAX_SOURCE_IDS
                || distinct(request.taskIds()).size() > MAX_SOURCE_IDS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单类版本来源标识单次最多查询 100 个");
        }
    }

    /**
     * Feign远程调用结果断言工具
     * 校验返回结果状态，非成功则抛出业务异常；成功返回响应数据
     * @param result 远程服务返回结果
     * @return 响应的业务数据
     * @param <T> 泛型返回数据类型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}