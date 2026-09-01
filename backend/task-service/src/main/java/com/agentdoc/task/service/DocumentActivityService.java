package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.DocumentActivityType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.param.DocumentActivitySearchParam;
import com.agentdoc.task.pojo.vo.DocumentActivityVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;

/**
 * 文档活动聚合服务。
 * <p>任务和变更请求仍由 task-service 自己查询，避免跨域直接访问 document-service 表。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentActivityService {

    private final TaskMapper taskMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final DocumentFeign documentFeign;
    private final AuthFeign authFeign;

    /**
     * 查询文档相关的任务与变更请求活动，并按活动时间倒序分页。
     *
     * @param param 文档 ID 和分页参数
     * @return 聚合活动分页结果
     */
    public PageVO<DocumentActivityVO> list(DocumentActivitySearchParam param) {
        DocumentRefVO document = requireDocument(param.documentId());
        requirePermission(document.spaceId(), TASK_READ);
        requirePermission(document.spaceId(), CHANGE_REQUEST_READ);

        List<TaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getDocumentId, param.documentId()));
        List<ChangeRequestEntity> changeRequests = changeRequestMapper.selectList(
                new LambdaQueryWrapper<ChangeRequestEntity>()
                        .eq(ChangeRequestEntity::getDocumentId, param.documentId()));
        Map<Long, UserRefVO> users = fetchUsers(Stream.concat(
                        tasks.stream().map(TaskEntity::getCreatedBy).filter(Objects::nonNull),
                        changeRequests.stream().map(ChangeRequestEntity::getProposedBy).filter(Objects::nonNull))
                .distinct()
                .toList());

        List<ActivityRow> rows = new ArrayList<>();
        tasks.forEach(task -> rows.add(new ActivityRow(
                new DocumentActivityVO(task.getId(), DocumentActivityType.TASK, task.getName(),
                        taskStatusName(task.getStatus()), task.getId(),
                        displayName(users.get(task.getCreatedBy())), activityTime(task)),
                activityTime(task))));
        changeRequests.forEach(request -> rows.add(new ActivityRow(
                new DocumentActivityVO(request.getId(), DocumentActivityType.CHANGE_REQUEST,
                        "文档变更请求", changeRequestStatusName(request.getStatus()), request.getSourceTaskId(),
                        displayName(users.get(request.getProposedBy())), request.getCreatedAt()),
                request.getCreatedAt())));
        rows.sort(Comparator.comparing(ActivityRow::activityAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        PageParam pageParam = param.pageParam() == null ? new PageParam() : param.pageParam();
        pageParam.validate();
        int from = Math.min((pageParam.getPageNum() - 1) * pageParam.getPageSize(), rows.size());
        int to = Math.min(from + pageParam.getPageSize(), rows.size());
        List<DocumentActivityVO> records = rows.subList(from, to).stream()
                .map(ActivityRow::activity)
                .toList();
        return PageVO.of(records, rows.size(), pageParam);
    }

    private DocumentRefVO requireDocument(Long documentId) {
        List<DocumentRefVO> documents = requireData(documentFeign.getDocumentRefs(List.of(documentId)));
        if (documents.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return documents.get(0);
    }

    private void requirePermission(Long spaceId, String permissionCode) {
        requireData(documentFeign.checkSpacePermission(spaceId, permissionCode));
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
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    private LocalDateTime activityTime(TaskEntity task) {
        if (task.getLastHeartbeatAt() != null) {
            return task.getLastHeartbeatAt();
        }
        if (task.getEndTime() != null) {
            return task.getEndTime();
        }
        if (task.getStartTime() != null) {
            return task.getStartTime();
        }
        return task.getCreatedAt();
    }

    private String taskStatusName(Integer status) {
        TaskStatus taskStatus = TaskStatus.fromCode(status);
        return taskStatus == null ? null : taskStatus.getName();
    }

    private String changeRequestStatusName(Integer status) {
        ChangeRequestStatus requestStatus = ChangeRequestStatus.fromCode(status);
        return requestStatus == null ? null : requestStatus.getName();
    }

    private <T> T requireData(Result<T> result) {
        if (result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result.code(), result.message());
        }
        return result.data();
    }

    private record ActivityRow(DocumentActivityVO activity, LocalDateTime activityAt) {
    }
}
