package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ApprovalMergeRequestDTO;
import com.agentdoc.common.feign.dto.DocumentChangePreviewRequestDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.DocumentChangePreviewVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.convertor.ChangeRequestConvertor;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.enums.ChangeRequestResolutionType;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import com.agentdoc.task.mapper.ChangeRequestCommentMapper;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.dto.ChangeRequestApproveDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestBatchDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestCommentDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestReviewDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.entity.ChangeRequestCommentEntity;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.BatchChangeRequestResultVO;
import com.agentdoc.task.pojo.vo.ChangeRequestCommentVO;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_APPROVE;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_MERGE;

/** 变更请求认领、批注、审批和合并服务。 */
@Service
@RequiredArgsConstructor
public class ChangeRequestReviewService {

    private final ChangeRequestService changeRequestService;
    private final ChangeRequestMapper changeRequestMapper;
    private final ChangeRequestCommentMapper commentMapper;
    private final DocumentFeign documentFeign;
    private final AuthFeign authFeign;
    private final AuditLogService auditLogService;
    private final TaskService taskService;

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO submit(ChangeRequestSubmitDTO dto) {
        if (dto.requestType() != ChangeRequestType.FORMAL) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有正式文档可以提交变更请求");
        }
        DocumentChangePreviewVO preview = requireData(documentFeign.previewSubmittedDocumentChanges(
                new DocumentChangePreviewRequestDTO(dto.documentId(), dto.baseVersion(), dto.changes())));
        if (preview.conflicted()) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档版本已变化，请刷新后重新提交");
        }
        ChangeRequestVO result = changeRequestService.submit(dto);
        DocumentRefVO document = requireData(documentFeign.getDocumentRefs(List.of(dto.documentId()))).getFirst();
        auditLogService.recordHuman(document.spaceId(), AuditAction.CHANGE_REQUEST_SUBMITTED,
                AuditTargetType.CHANGE_REQUEST, result.id(), dto.summary());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO claim(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();
        int updated = changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .and(condition -> condition.isNull(ChangeRequestEntity::getAssignedReviewerId)
                        .or().eq(ChangeRequestEntity::getAssignedReviewerId, userId))
                .set(ChangeRequestEntity::getAssignedReviewerId, userId));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求已被其他审批人认领或状态已变化");
        }
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_CLAIMED,
                AuditTargetType.CHANGE_REQUEST, id, null);
        return toVO(requireRequest(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO unclaim(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();
        int updated = changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .eq(ChangeRequestEntity::getAssignedReviewerId, userId)
                .set(ChangeRequestEntity::getAssignedReviewerId, null));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能释放自己认领的待审批请求");
        }
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_UNCLAIMED,
                AuditTargetType.CHANGE_REQUEST, id, null);
        return toVO(requireRequest(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO approve(Long id, ChangeRequestApproveDTO dto) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();
        requireAvailableAssignee(entity, userId);
        validateResolution(dto);
        String comment = requireComment(dto.reviewComment(), "审批意见不能为空");
        int updated = changeRequestMapper.update(null, pendingDecision(id, userId)
                .set(ChangeRequestEntity::getStatus, ChangeRequestStatus.APPROVED.getCode())
                .set(ChangeRequestEntity::getReviewComment, comment)
                .set(ChangeRequestEntity::getReviewedBy, userId)
                .set(ChangeRequestEntity::getReviewedAt, LocalDateTime.now())
                .set(ChangeRequestEntity::getResolutionType, dto.resolutionType().name())
                .set(ChangeRequestEntity::getAcceptedChangeKeys, JsonUtils.toJson(
                        dto.acceptedChangeKeys() == null ? List.of() : dto.acceptedChangeKeys()))
                .set(ChangeRequestEntity::getResolvedContent,
                        dto.resolutionType() == ChangeRequestResolutionType.ALL ? null : dto.resolvedContent()));
        requireUpdated(updated);
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_APPROVED,
                AuditTargetType.CHANGE_REQUEST, id, dto.resolutionType().name());
        return toVO(requireRequest(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO reject(Long id, ChangeRequestReviewDTO dto) {
        return decide(id, ChangeRequestStatus.REJECTED,
                requireComment(dto.reviewComment(), "拒绝原因不能为空"), AuditAction.CHANGE_REQUEST_REJECTED);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO returnRequest(Long id, ChangeRequestReviewDTO dto) {
        String comment = requireComment(dto.reviewComment(), "退回批注不能为空");
        ChangeRequestVO result = decide(id, ChangeRequestStatus.RETURNED, comment,
                AuditAction.CHANGE_REQUEST_RETURNED);
        ChangeRequestEntity entity = requireRequest(id);
        if (entity.getSourceTaskId() != null && entity.getReworkTaskId() == null) {
            TaskVO rework = taskService.createReviewRework(entity.getSourceTaskId(), id, comment);
            changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                    .eq(ChangeRequestEntity::getId, id)
                    .isNull(ChangeRequestEntity::getReworkTaskId)
                    .set(ChangeRequestEntity::getReworkTaskId, rework.id()));
            auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_REWORK_CREATED,
                    AuditTargetType.CHANGE_REQUEST, id, String.valueOf(rework.id()));
            result = toVO(requireRequest(id));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO merge(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_MERGE);
        ChangeRequestStatus current = ChangeRequestStatus.fromCode(entity.getStatus());
        if (current == ChangeRequestStatus.MERGED) {
            return toVO(entity);
        }
        if (current != ChangeRequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已通过的变更请求可合并");
        }
        MergeResultVO merged;
        try {
            merged = requireData(documentFeign.mergeApprovedDocument(new ApprovalMergeRequestDTO(
                    entity.getId(), entity.getSourceTaskId(), entity.getDocumentId(), entity.getBaseVersion(),
                    ChangeRequestConvertor.parseChanges(entity.getChanges()), entity.getResolvedContent(),
                    entity.getSummary())));
        } catch (FeignException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "合并服务调用失败：" + exception.status());
        }
        Long userId = AuthUtils.getUserIdOrException();
        int updated = changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.APPROVED.getCode())
                .set(ChangeRequestEntity::getStatus, ChangeRequestStatus.MERGED.getCode())
                .set(ChangeRequestEntity::getMergedBy, userId)
                .set(ChangeRequestEntity::getMergedAt, LocalDateTime.now())
                .set(ChangeRequestEntity::getMergedVersion, merged.newVersion()));
        if (updated == 0) {
            ChangeRequestEntity latest = requireRequest(id);
            if (ChangeRequestStatus.fromCode(latest.getStatus()) == ChangeRequestStatus.MERGED) {
                return toVO(latest);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "审批状态已变化，请刷新后重试");
        }
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_MERGED,
                AuditTargetType.CHANGE_REQUEST, id, "v" + merged.newVersion());
        return toVO(requireRequest(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestCommentVO addComment(Long id, ChangeRequestCommentDTO dto) {
        ChangeRequestEntity request = requireRequest(id);
        requirePermission(request.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();
        ChangeRequestCommentEntity entity = new ChangeRequestCommentEntity();
        entity.setChangeRequestId(id);
        entity.setChangeKey(trimToNull(dto.changeKey()));
        entity.setAuthorId(userId);
        entity.setContent(dto.content().trim());
        commentMapper.insert(entity);
        auditLogService.recordHuman(request.getSpaceId(), AuditAction.CHANGE_REQUEST_COMMENTED,
                AuditTargetType.CHANGE_REQUEST, id, entity.getChangeKey());
        List<UserRefVO> users = requireData(authFeign.queryUsers(new UserBatchQueryDTO(List.of(userId))));
        String authorName = users.isEmpty() ? null : displayName(users.getFirst());
        return new ChangeRequestCommentVO(entity.getId(), entity.getChangeKey(), userId,
                authorName, entity.getContent(), entity.getCreatedAt());
    }

    public BatchChangeRequestResultVO batchApprove(ChangeRequestBatchDTO dto) {
        return batch(dto.ids(), id -> approve(id, new ChangeRequestApproveDTO(
                ChangeRequestResolutionType.ALL, dto.reviewComment(), List.of(), null)));
    }

    public BatchChangeRequestResultVO batchAccept(ChangeRequestBatchDTO dto) {
        return batch(dto.ids(), id -> {
            approve(id, new ChangeRequestApproveDTO(
                    ChangeRequestResolutionType.ALL, dto.reviewComment(), List.of(), null));
            return merge(id);
        });
    }

    public BatchChangeRequestResultVO batchReject(ChangeRequestBatchDTO dto) {
        requireComment(dto.reviewComment(), "批量拒绝原因不能为空");
        return batch(dto.ids(), id -> reject(id, new ChangeRequestReviewDTO(dto.reviewComment())));
    }

    public BatchChangeRequestResultVO batchMerge(ChangeRequestBatchDTO dto) {
        return batch(dto.ids(), this::merge);
    }

    private ChangeRequestVO decide(Long id, ChangeRequestStatus target, String comment, AuditAction action) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();
        requireAvailableAssignee(entity, userId);
        int updated = changeRequestMapper.update(null, pendingDecision(id, userId)
                .set(ChangeRequestEntity::getStatus, target.getCode())
                .set(ChangeRequestEntity::getReviewComment, comment)
                .set(ChangeRequestEntity::getReviewedBy, userId)
                .set(ChangeRequestEntity::getReviewedAt, LocalDateTime.now()));
        requireUpdated(updated);
        auditLogService.recordHuman(entity.getSpaceId(), action, AuditTargetType.CHANGE_REQUEST, id, comment);
        return toVO(requireRequest(id));
    }

    private LambdaUpdateWrapper<ChangeRequestEntity> pendingDecision(Long id, Long userId) {
        return new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .and(condition -> condition.isNull(ChangeRequestEntity::getAssignedReviewerId)
                        .or().eq(ChangeRequestEntity::getAssignedReviewerId, userId))
                .set(ChangeRequestEntity::getAssignedReviewerId, userId);
    }

    private void validateResolution(ChangeRequestApproveDTO dto) {
        if (dto.resolutionType() == ChangeRequestResolutionType.PARTIAL) {
            if (dto.acceptedChangeKeys() == null || dto.acceptedChangeKeys().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "部分接受必须选择至少一个 Diff 块");
            }
            if (dto.resolvedContent() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "部分接受必须提交确认后的正文");
            }
        }
        if (dto.resolutionType() == ChangeRequestResolutionType.EDITED && dto.resolvedContent() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "修改后接受必须提交编辑后的正文");
        }
    }

    private BatchChangeRequestResultVO batch(List<Long> ids, BatchOperation operation) {
        List<Long> succeeded = new ArrayList<>();
        List<BatchChangeRequestResultVO.Failure> failures = new ArrayList<>();
        ids.stream().distinct().forEach(id -> {
            try {
                operation.apply(id);
                succeeded.add(id);
            } catch (BusinessException exception) {
                failures.add(new BatchChangeRequestResultVO.Failure(id, exception.getCode(), exception.getMessage()));
            }
        });
        return new BatchChangeRequestResultVO(succeeded, failures);
    }

    private ChangeRequestEntity requireRequest(Long id) {
        return changeRequestService.requireRequest(id);
    }

    private ChangeRequestVO toVO(ChangeRequestEntity entity) {
        List<DocumentRefVO> documents = requireData(documentFeign.getDocumentRefs(List.of(entity.getDocumentId())));
        String title = documents.isEmpty() ? null : documents.getFirst().title();
        return ChangeRequestConvertor.toVO(entity, title);
    }

    private String requireComment(String value, String message) {
        String comment = trimToNull(value);
        if (comment == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return comment;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireAvailableAssignee(ChangeRequestEntity entity, Long userId) {
        if (entity.getAssignedReviewerId() != null && !Objects.equals(entity.getAssignedReviewerId(), userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求已被其他审批人认领");
        }
    }

    private void requireUpdated(int updated) {
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "审批状态已变化，请刷新后重试");
        }
    }

    private void requirePermission(Long spaceId, String permission) {
        requireData(documentFeign.checkSpacePermission(spaceId, permission));
    }

    private String displayName(UserRefVO user) {
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }

    @FunctionalInterface
    private interface BatchOperation {
        ChangeRequestVO apply(Long id);
    }
}
