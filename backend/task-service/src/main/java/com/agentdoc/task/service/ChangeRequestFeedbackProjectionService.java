package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;

/**
 * 变更请求反馈快照投影服务
 * <p>
 * 提取变更请求审批终态事实，生成不可篡改的反馈快照VO，用于评估链路导入；
 * 仅在变更请求到达最终审批状态时才可获取，同时做空间权限校验，返回摘要哈希，不暴露完整评审原文。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestFeedbackProjectionService {
    /**
     * 变更请求的最终状态集合，只有处于这些状态才具备可导入评估的审批事实
     */
    private static final Set<ChangeRequestStatus> FINAL_STATUSES = Set.of(ChangeRequestStatus.APPROVED,
            ChangeRequestStatus.REJECTED, ChangeRequestStatus.MERGED, ChangeRequestStatus.RETURNED);

    private final ChangeRequestMapper changeRequestMapper;
    private final TaskMapper taskMapper;
    private final DocumentFeign documentFeign;

    /**
     * 获取变更请求审批反馈快照
     * <p>
     * 校验变更请求存在性、状态是否为终态；校验空间权限；
     * 计算评审意见哈希与整体快照源哈希，返回轻量化快照VO供给评估服务使用。
     * </p>
     * @param id 变更请求主键ID
     * @return ChangeRequestFeedbackSnapshotVO 变更请求反馈快照VO
     * @throws BusinessException 记录不存在、状态非终态、权限校验失败时抛出异常
     */
    public ChangeRequestFeedbackSnapshotVO get(Long id) {
        ChangeRequestEntity changeRequest = changeRequestMapper.selectById(id);
        ChangeRequestStatus status = changeRequest == null ? null
                : ChangeRequestStatus.fromCode(changeRequest.getStatus());
        // 校验：变更请求存在、状态合法、并且属于终态，才允许导出评估快照
        if (changeRequest == null || status == null || !FINAL_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "ChangeRequest 尚未形成可导入的最终审批事实");
        }
        // 校验评估运行权限与任务读取权限
        requirePermission(changeRequest.getSpaceId(), EVALUATION_RUN);
        requirePermission(changeRequest.getSpaceId(), TASK_READ);
        // 查询关联的源任务
        TaskEntity task = changeRequest.getSourceTaskId() == null ? null
                : taskMapper.selectById(changeRequest.getSourceTaskId());
        // 对评审意见文本计算sha256哈希，不存储原文
        String commentHash = changeRequest.getReviewComment() == null ? null
                : StableSnapshotUtils.sha256Utf8(changeRequest.getReviewComment());
        // 生成变更请求整体快照哈希，用于完整性校验
        String sourceHash = StableSnapshotUtils.snapshotHash(1, Map.ofEntries(
                Map.entry("changeRequestId", changeRequest.getId()),
                Map.entry("status", status.name()),
                Map.entry("resolutionType", value(changeRequest.getResolutionType())),
                Map.entry("reviewCommentHash", value(commentHash)),
                Map.entry("reviewedBy", value(changeRequest.getReviewedBy())),
                Map.entry("reviewedAt", changeRequest.getReviewedAt() == null
                        ? "" : changeRequest.getReviewedAt().toString()),
                Map.entry("revisionNo", value(changeRequest.getRevisionNo()))));
        // 组装快照VO返回
        return new ChangeRequestFeedbackSnapshotVO(changeRequest.getId(), changeRequest.getSpaceId(),
                changeRequest.getSourceTaskId(), task == null ? null : task.getAgentExecutionId(), status.name(),
                changeRequest.getResolutionType(), commentHash, changeRequest.getReviewedBy(),
                changeRequest.getReviewedAt(), changeRequest.getRevisionNo(), sourceHash);
    }

    /**
     * 校验空间权限，调用文档服务feign接口
     * @param spaceId 空间ID
     * @param permission 待校验权限标识
     * @throws BusinessException 权限不足或远程调用异常抛出业务异常
     */
    private void requirePermission(Long spaceId, String permission) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, permission);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "权限校验服务不可用" : result.message());
        }
    }

    /**
     * 快照哈希辅助方法：null值转为空字符串，保证哈希计算稳定
     * @param value 原始字段值
     * @return 原值或空字符串
     */
    private Object value(Object value) {
        return value == null ? "" : value;
    }
}
