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

/** 批量聚合文档版本关联的任务、Agent 与审批信息。 */
@Service
@RequiredArgsConstructor
public class DocumentVersionSourceQueryService {

    private static final int MAX_SOURCE_IDS = 100;

    private final ChangeRequestMapper changeRequestMapper;
    private final TaskMapper taskMapper;
    private final DocumentFeign documentFeign;
    private final AgentFeign agentFeign;
    private final AuthFeign authFeign;

    public List<DocumentVersionSourceVO> query(DocumentVersionSourceQueryDTO request) {
        validate(request);
        requireData(documentFeign.checkSpacePermission(request.spaceId(), DOCUMENT_READ));
        List<Long> changeRequestIds = distinct(request.changeRequestIds());
        List<ChangeRequestEntity> requests = changeRequestIds.isEmpty() ? List.of()
                : changeRequestMapper.selectList(new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSpaceId, request.spaceId())
                .in(ChangeRequestEntity::getId, changeRequestIds));
        Set<Long> taskIds = new LinkedHashSet<>(distinct(request.taskIds()));
        requests.stream().map(ChangeRequestEntity::getSourceTaskId)
                .filter(Objects::nonNull).forEach(taskIds::add);
        Map<Long, TaskEntity> tasks = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getSpaceId, request.spaceId())
                .in(TaskEntity::getId, taskIds)).stream()
                .collect(Collectors.toMap(TaskEntity::getId, Function.identity()));
        Map<Long, AgentExecutionTokenUsageBatchVO> executionTokens = fetchExecutionTokens(tasks.values());
        Map<Long, AgentRefVO> agents = fetchAgents(tasks.values().stream()
                .map(TaskEntity::getAgentId).filter(Objects::nonNull).distinct().toList());
        Set<Long> userIds = new LinkedHashSet<>();
        tasks.values().stream().map(TaskEntity::getCreatedBy).filter(Objects::nonNull).forEach(userIds::add);
        requests.stream().map(ChangeRequestEntity::getReviewedBy).filter(Objects::nonNull).forEach(userIds::add);
        requests.stream().map(ChangeRequestEntity::getMergedBy).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, UserRefVO> users = fetchUsers(new ArrayList<>(userIds));

        List<DocumentVersionSourceVO> result = new ArrayList<>();
        Set<Long> representedTaskIds = new LinkedHashSet<>();
        for (ChangeRequestEntity changeRequest : requests) {
            TaskEntity task = getOrNull(tasks, changeRequest.getSourceTaskId());
            if (task != null) representedTaskIds.add(task.getId());
            result.add(toVO(changeRequest, task, agents, users, executionTokens));
        }
        tasks.values().stream()
                .filter(task -> !representedTaskIds.contains(task.getId()))
                .map(task -> toVO(null, task, agents, users, executionTokens))
                .forEach(result::add);
        return result;
    }

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
        if (tokensUsed == null && execution != null
                && execution.inputTokens() != null && execution.outputTokens() != null) {
            tokensUsed = execution.inputTokens() + execution.outputTokens();
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

    private Map<Long, AgentRefVO> fetchAgents(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<AgentRefVO> rows = requireData(agentFeign.queryAgentRefs(new AgentBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().filter(Objects::nonNull).filter(row -> row.id() != null)
                .collect(Collectors.toMap(AgentRefVO::id, Function.identity(), (left, right) -> left));
    }

    private Map<Long, UserRefVO> fetchUsers(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UserRefVO> rows = requireData(authFeign.queryUsers(new UserBatchQueryDTO(ids)));
        if (rows == null || rows.isEmpty()) return Map.of();
        return rows.stream().filter(Objects::nonNull).filter(row -> row.id() != null)
                .collect(Collectors.toMap(UserRefVO::id, Function.identity(), (left, right) -> left));
    }

    private String agentName(AgentRefVO agent) {
        return agent == null ? null : agent.name();
    }

    private String userName(UserRefVO user) {
        if (user == null) return null;
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    private <T> T getOrNull(Map<Long, T> values, Long id) {
        return id == null ? null : values.get(id);
    }

    private List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private void validate(DocumentVersionSourceQueryDTO request) {
        if (request == null || request.spaceId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "版本来源查询缺少空间 ID");
        }
        if (distinct(request.changeRequestIds()).size() > MAX_SOURCE_IDS
                || distinct(request.taskIds()).size() > MAX_SOURCE_IDS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单类版本来源标识单次最多查询 100 个");
        }
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}
