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
 * <p>任务和变更请求仍由 task‑service 自己查询，避免跨域直接访问 document‑service 表。</p>
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
     * <p>逻辑：本地查询任务、变更请求；拉取操作用户信息；组装活动记录；内存排序；内存分页返回。</p>
     *
     * @param param 文档 ID 和分页参数
     * @return 聚合活动分页结果
     */
    public PageVO<DocumentActivityVO> list(DocumentActivitySearchParam param) {
        // 校验文档存在，并校验空间下任务、变更请求的读取权限
        DocumentRefVO document = requireDocument(param.documentId());
        requirePermission(document.spaceId(), TASK_READ);
        requirePermission(document.spaceId(), CHANGE_REQUEST_READ);

        // 本地查询该文档下全部任务
        List<TaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getDocumentId, param.documentId()));
        // 本地查询该文档下全部变更请求
        List<ChangeRequestEntity> changeRequests = changeRequestMapper.selectList(
                new LambdaQueryWrapper<ChangeRequestEntity>()
                        .eq(ChangeRequestEntity::getDocumentId, param.documentId()));

        // 提取所有任务创建人、变更请求提交人ID，去重，批量查询用户信息
        Map<Long, UserRefVO> users = fetchUsers(Stream.concat(
                        tasks.stream().map(TaskEntity::getCreatedBy).filter(Objects::nonNull),
                        changeRequests.stream().map(ChangeRequestEntity::getProposedBy).filter(Objects::nonNull))
                .distinct()
                .toList());

        List<ActivityRow> rows = new ArrayList<>();
        // 组装任务类型活动记录
        tasks.forEach(task -> rows.add(new ActivityRow(
                new DocumentActivityVO(task.getId(), DocumentActivityType.TASK, task.getName(),
                        taskStatusName(task.getStatus()), task.getId(),
                        displayName(users.get(task.getCreatedBy())), activityTime(task)),
                activityTime(task))));
        // 组装变更请求类型活动记录
        changeRequests.forEach(request -> rows.add(new ActivityRow(
                new DocumentActivityVO(request.getId(), DocumentActivityType.CHANGE_REQUEST,
                        "文档变更请求", changeRequestStatusName(request.getStatus()), request.getSourceTaskId(),
                        displayName(users.get(request.getProposedBy())), request.getCreatedAt()),
                request.getCreatedAt())));

        // 全部活动按活动时间倒序排序，null时间放末尾
        rows.sort(Comparator.comparing(ActivityRow::activityAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 处理分页参数，使用内存分页（全部数据加载到内存后截取）
        PageParam pageParam = param.pageParam() == null ? new PageParam() : param.pageParam();
        pageParam.validate();
        int from = Math.min((pageParam.getPageNum() - 1) * pageParam.getPageSize(), rows.size());
        int to = Math.min(from + pageParam.getPageSize(), rows.size());
        List<DocumentActivityVO> records = rows.subList(from, to).stream()
                .map(ActivityRow::activity)
                .toList();

        return PageVO.of(records, rows.size(), pageParam);
    }

    /**
     * 获取文档引用信息，文档不存在抛出异常
     * @param documentId 文档ID
     * @return 文档引用VO
     */
    private DocumentRefVO requireDocument(Long documentId) {
        List<DocumentRefVO> documents = requireData(documentFeign.getDocumentRefs(List.of(documentId)));
        if (documents.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return documents.get(0);
    }

    /**
     * 校验空间权限，不满足则抛业务异常
     * @param spaceId 空间ID
     * @param permissionCode 权限编码
     */
    private void requirePermission(Long spaceId, String permissionCode) {
        requireData(documentFeign.checkSpacePermission(spaceId, permissionCode));
    }

    /**
     * 批量查询用户，返回 userId -> UserRefVO 的Map
     * @param userIds 用户ID列表
     * @return 用户映射；入参为空或远程返回异常返回空Map
     */
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

    /**
     * 获取用户展示名称：优先昵称，昵称为空则使用用户名
     * @param user 用户引用对象
     * @return 展示名称，null返回null
     */
    private String displayName(UserRefVO user) {
        if (user == null) {
            return null;
        }
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    /**
     * 获取任务的活动时间，优先级：最后心跳 > 结束时间 > 开始时间 > 创建时间
     * @param task 任务实体
     * @return 用于排序的活动时间
     */
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

    /**
     * 任务状态码转展示文本
     * @param status 状态数字编码
     * @return 状态名称，找不到返回null
     */
    private String taskStatusName(Integer status) {
        TaskStatus taskStatus = TaskStatus.fromCode(status);
        return taskStatus == null ? null : taskStatus.getName();
    }

    /**
     * 变更请求状态码转展示文本
     * @param status 状态数字编码
     * @return 状态名称，找不到返回null
     */
    private String changeRequestStatusName(Integer status) {
        ChangeRequestStatus requestStatus = ChangeRequestStatus.fromCode(status);
        return requestStatus == null ? null : requestStatus.getName();
    }

    /**
     * Feign调用结果断言工具，校验返回成功，失败抛业务异常
     * @param result 远程调用返回结果
     * @return 响应数据
     * @param <T> 返回数据泛型
     */
    private <T> T requireData(Result<T> result) {
        if (result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result.code(), result.message());
        }
        return result.data();
    }

    /**
     * 记录行：封装活动VO和对应的排序时间
     * @param activity 活动VO
     * @param activityAt 用于排序的时间
     */
    private record ActivityRow(DocumentActivityVO activity, LocalDateTime activityAt) {
    }
}