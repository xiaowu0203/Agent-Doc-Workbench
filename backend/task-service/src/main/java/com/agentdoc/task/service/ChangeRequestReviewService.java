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

    /**
     * 提交变更请求
     * 仅允许正式文档提交变更请求；先校验文档变更是否存在冲突，无冲突则创建变更请求，并记录审计日志
     * @param dto 提交变更请求入参
     * @return 变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO submit(ChangeRequestSubmitDTO dto) {
        // 仅正式文档可提交变更请求
        if (dto.requestType() != ChangeRequestType.FORMAL) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有正式文档可以提交变更请求");
        }
        // 调用文档服务预览变更，校验是否存在版本冲突
        DocumentChangePreviewVO preview = requireData(documentFeign.previewSubmittedDocumentChanges(
                new DocumentChangePreviewRequestDTO(dto.documentId(), dto.baseVersion(), dto.changes())));
        if (preview.conflicted()) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档版本已变化，请刷新后重新提交");
        }
        // 委托ChangeRequestService完成变更请求创建
        ChangeRequestVO result = changeRequestService.submit(dto);
        // 获取文档信息用于审计日志
        DocumentRefVO document = requireData(documentFeign.getDocumentRefs(List.of(dto.documentId()))).getFirst();
        // 记录提交变更请求审计日志
        auditLogService.recordHuman(document.spaceId(), AuditAction.CHANGE_REQUEST_SUBMITTED,
                AuditTargetType.CHANGE_REQUEST, result.id(), dto.summary());
        return result;
    }

    /**
     * 认领变更请求
     * 将待处理状态的变更请求分配给当前登录用户；
     * 乐观更新，仅当状态为PENDING且未分配/已分配给自己时可认领
     * @param id 变更请求ID
     * @return 更新后的变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO claim(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        // 校验空间审批权限
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();

        // 乐观更新：状态为待处理，未分配或者分配给自己，更新为当前用户为评审人
        int updated = changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .and(condition -> condition.isNull(ChangeRequestEntity::getAssignedReviewerId)
                        .or().eq(ChangeRequestEntity::getAssignedReviewerId, userId))
                .set(ChangeRequestEntity::getAssignedReviewerId, userId));

        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求已被其他审批人认领或状态已变化");
        }
        // 记录认领审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_CLAIMED,
                AuditTargetType.CHANGE_REQUEST, id, null);
        return toVO(requireRequest(id));
    }

    /**
     * 释放（取消认领）变更请求
     * 仅可释放自己认领的待处理变更请求，清空评审人
     * @param id 变更请求ID
     * @return 更新后的变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO unclaim(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();

        // 乐观更新：状态待处理，且评审人是当前用户，置空评审人
        int updated = changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .eq(ChangeRequestEntity::getAssignedReviewerId, userId)
                .set(ChangeRequestEntity::getAssignedReviewerId, null));

        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能释放自己认领的待审批请求");
        }
        // 记录释放认领审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_UNCLAIMED,
                AuditTargetType.CHANGE_REQUEST, id, null);
        return toVO(requireRequest(id));
    }

    /**
     * 审批通过变更请求
     * 校验权限、评审人归属；校验审批参数；更新状态为APPROVED，保存审批意见、决议类型、接受变更块、解析后的内容
     * @param id 变更请求ID
     * @param dto 审批入参
     * @return 更新后的变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO approve(Long id, ChangeRequestApproveDTO dto) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_APPROVE);
        Long userId = AuthUtils.getUserIdOrException();
        // 校验当前用户是否可以处理该请求（不能处理别人认领的）
        requireAvailableAssignee(entity, userId);
        // 校验决议类型对应的参数合法性
        validateResolution(dto);
        // 校验审批意见非空
        String comment = requireComment(dto.reviewComment(), "审批意见不能为空");

        // 乐观更新变更请求：标记为已通过，保存审批相关字段
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
        // 记录审批通过审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_APPROVED,
                AuditTargetType.CHANGE_REQUEST, id, dto.resolutionType().name());
        return toVO(requireRequest(id));
    }

    /**
     * 拒绝变更请求
     * 更新状态为REJECTED，保存拒绝意见，记录审计日志
     * @param id 变更请求ID
     * @param dto 拒绝入参
     * @return 更新后的变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO reject(Long id, ChangeRequestReviewDTO dto) {
        return decide(id, ChangeRequestStatus.REJECTED,
                requireComment(dto.reviewComment(), "拒绝原因不能为空"), AuditAction.CHANGE_REQUEST_REJECTED);
    }

    /**
     * 退回变更请求
     * 更新状态为RETURNED；如果存在源任务，创建返工任务并关联到变更请求，记录审计日志
     * @param id 变更请求ID
     * @param dto 退回入参
     * @return 更新后的变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO returnRequest(Long id, ChangeRequestReviewDTO dto) {
        String comment = requireComment(dto.reviewComment(), "退回批注不能为空");
        ChangeRequestVO result = decide(id, ChangeRequestStatus.RETURNED, comment,
                AuditAction.CHANGE_REQUEST_RETURNED);

        ChangeRequestEntity entity = requireRequest(id);
        // 存在源任务且尚未创建返工任务，则创建返工任务
        if (entity.getSourceTaskId() != null && entity.getReworkTaskId() == null) {
            TaskVO rework = taskService.createReviewRework(entity.getSourceTaskId(), id, comment);
            // 更新变更请求关联返工任务ID
            changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                    .eq(ChangeRequestEntity::getId, id)
                    .isNull(ChangeRequestEntity::getReworkTaskId)
                    .set(ChangeRequestEntity::getReworkTaskId, rework.id()));
            // 记录创建返工任务审计日志
            auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_REWORK_CREATED,
                    AuditTargetType.CHANGE_REQUEST, id, String.valueOf(rework.id()));
            result = toVO(requireRequest(id));
        }
        return result;
    }

    /**
     * 合并已审批通过的变更请求
     * 仅APPROVED状态可合并；调用文档服务执行合并；更新变更请求状态为MERGED，记录合并人、合并时间、合并后的版本号
     * @param id 变更请求ID
     * @return 合并后的变更请求VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO merge(Long id) {
        ChangeRequestEntity entity = requireRequest(id);
        requirePermission(entity.getSpaceId(), CHANGE_REQUEST_MERGE);
        ChangeRequestStatus current = ChangeRequestStatus.fromCode(entity.getStatus());

        // 已经合并直接返回
        if (current == ChangeRequestStatus.MERGED) {
            return toVO(entity);
        }
        // 仅已通过状态允许合并
        if (current != ChangeRequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已通过的变更请求可合并");
        }

        MergeResultVO merged;
        try {
            // 调用文档服务执行合并
            merged = requireData(documentFeign.mergeApprovedDocument(new ApprovalMergeRequestDTO(
                    entity.getId(), entity.getSourceTaskId(), entity.getDocumentId(), entity.getBaseVersion(),
                    ChangeRequestConvertor.parseChanges(entity.getChanges()), entity.getResolvedContent(),
                    entity.getSummary())));
        } catch (FeignException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "合并服务调用失败：" + exception.status());
        }

        Long userId = AuthUtils.getUserIdOrException();
        // 乐观更新：状态为已通过，更新为已合并
        int updated = changeRequestMapper.update(null, new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.APPROVED.getCode())
                .set(ChangeRequestEntity::getStatus, ChangeRequestStatus.MERGED.getCode())
                .set(ChangeRequestEntity::getMergedBy, userId)
                .set(ChangeRequestEntity::getMergedAt, LocalDateTime.now())
                .set(ChangeRequestEntity::getMergedVersion, merged.newVersion()));

        if (updated == 0) {
            ChangeRequestEntity latest = requireRequest(id);
            // 如果已经被别人合并，直接返回最新数据
            if (ChangeRequestStatus.fromCode(latest.getStatus()) == ChangeRequestStatus.MERGED) {
                return toVO(latest);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "审批状态已变化，请刷新后重试");
        }
        // 记录合并审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.CHANGE_REQUEST_MERGED,
                AuditTargetType.CHANGE_REQUEST, id, "v" + merged.newVersion());
        return toVO(requireRequest(id));
    }

    /**
     * 添加变更请求评论
     * 校验空间权限，插入评论记录，记录审计日志，返回评论VO
     * @param id 变更请求ID
     * @param dto 评论入参
     * @return 评论VO
     */
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

        // 记录评论审计日志
        auditLogService.recordHuman(request.getSpaceId(), AuditAction.CHANGE_REQUEST_COMMENTED,
                AuditTargetType.CHANGE_REQUEST, id, entity.getChangeKey());

        // 查询评论作者名称
        List<UserRefVO> users = requireData(authFeign.queryUsers(new UserBatchQueryDTO(List.of(userId))));
        String authorName = users.isEmpty() ? null : displayName(users.getFirst());

        return new ChangeRequestCommentVO(entity.getId(), entity.getChangeKey(), userId,
                authorName, entity.getContent(), entity.getCreatedAt());
    }

    /**
     * 批量审批通过变更请求
     * 批量执行approve，决议类型为全部接受
     * @param dto 批量操作参数
     * @return 批量执行结果（成功ID列表、失败列表）
     */
    public BatchChangeRequestResultVO batchApprove(ChangeRequestBatchDTO dto) {
        return batch(dto.ids(), id -> approve(id, new ChangeRequestApproveDTO(
                ChangeRequestResolutionType.ALL, dto.reviewComment(), List.of(), null)));
    }

    /**
     * 批量接受并合并变更请求
     * 先审批通过，再执行合并
     * @param dto 批量操作参数
     * @return 批量执行结果
     */
    public BatchChangeRequestResultVO batchAccept(ChangeRequestBatchDTO dto) {
        return batch(dto.ids(), id -> {
            approve(id, new ChangeRequestApproveDTO(
                    ChangeRequestResolutionType.ALL, dto.reviewComment(), List.of(), null));
            return merge(id);
        });
    }

    /**
     * 批量拒绝变更请求
     * @param dto 批量操作参数
     * @return 批量执行结果
     */
    public BatchChangeRequestResultVO batchReject(ChangeRequestBatchDTO dto) {
        requireComment(dto.reviewComment(), "批量拒绝原因不能为空");
        return batch(dto.ids(), id -> reject(id, new ChangeRequestReviewDTO(dto.reviewComment())));
    }

    /**
     * 批量合并变更请求
     * @param dto 批量操作参数
     * @return 批量执行结果
     */
    public BatchChangeRequestResultVO batchMerge(ChangeRequestBatchDTO dto) {
        return batch(dto.ids(), this::merge);
    }

    /**
     * 通用评审决策方法：拒绝/退回复用此逻辑
     * 更新状态、保存评审意见、评审人、评审时间，记录审计日志
     * @param id 变更请求ID
     * @param target 目标状态
     * @param comment 评审意见
     * @param action 审计动作
     * @return 更新后的变更请求VO
     */
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

    /**
     * 构建待决策变更请求的更新Wrapper
     * 条件：ID匹配、状态PENDING；未分配/分配给当前用户；自动把评审人设置为当前用户
     * @param id 变更请求ID
     * @param userId 当前操作人ID
     * @return LambdaUpdateWrapper
     */
    private LambdaUpdateWrapper<ChangeRequestEntity> pendingDecision(Long id, Long userId) {
        return new LambdaUpdateWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getId, id)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .and(condition -> condition.isNull(ChangeRequestEntity::getAssignedReviewerId)
                        .or().eq(ChangeRequestEntity::getAssignedReviewerId, userId))
                .set(ChangeRequestEntity::getAssignedReviewerId, userId);
    }

    /**
     * 校验审批决议参数合法性
     * - PARTIAL部分接受：必须传acceptedChangeKeys，并且传入resolvedContent
     * - EDITED编辑后接受：必须传入resolvedContent
     * @param dto 审批入参
     */
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

    /**
     * 批量操作通用工具
     * 遍历ID列表，执行传入的操作；捕获BusinessException，区分成功/失败，返回批量结果
     * @param ids 待处理ID集合
     * @param operation 单条执行逻辑
     * @return 批量操作结果VO
     */
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

    /**
     * 获取变更请求实体，不存在抛出异常
     * @param id 变更请求ID
     * @return 变更请求实体
     */
    private ChangeRequestEntity requireRequest(Long id) {
        return changeRequestService.requireRequest(id);
    }

    /**
     * 实体转VO，补充文档标题
     * @param entity 变更请求实体
     * @return 变更请求VO
     */
    private ChangeRequestVO toVO(ChangeRequestEntity entity) {
        List<DocumentRefVO> documents = requireData(documentFeign.getDocumentRefs(List.of(entity.getDocumentId())));
        String title = documents.isEmpty() ? null : documents.getFirst().title();
        return ChangeRequestConvertor.toVO(entity, title);
    }

    /**
     * 校验评论非空，去除空白
     * @param value 原始评论
     * @param message 报错提示
     * @return 清理后的评论文本
     */
    private String requireComment(String value, String message) {
        String comment = trimToNull(value);
        if (comment == null) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return comment;
    }

    /**
     * 字符串trim，空白返回null
     * @param value 原始字符串
     * @return 处理后字符串，空白返回null
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 校验当前用户是否可以处理该变更请求
     * 如果已经被别人认领，则抛出冲突异常
     * @param entity 变更请求
     * @param userId 当前操作人ID
     */
    private void requireAvailableAssignee(ChangeRequestEntity entity, Long userId) {
        if (entity.getAssignedReviewerId() != null && !Objects.equals(entity.getAssignedReviewerId(), userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求已被其他审批人认领");
        }
    }

    /**
     * 校验乐观更新受影响行数，0则抛出冲突异常
     * @param updated update返回受影响行数
     */
    private void requireUpdated(int updated) {
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "审批状态已变化，请刷新后重试");
        }
    }

    /**
     * 校验空间权限
     * @param spaceId 空间ID
     * @param permission 权限标识
     */
    private void requirePermission(Long spaceId, String permission) {
        requireData(documentFeign.checkSpacePermission(spaceId, permission));
    }

    /**
     * 获取用户展示名称，优先昵称，无则用户名
     * @param user 用户引用对象
     * @return 展示名称
     */
    private String displayName(UserRefVO user) {
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    /**
     * Feign调用结果断言工具
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

    /**
     * 批量操作函数式接口，用于批量处理变更请求
     */
    @FunctionalInterface
    private interface BatchOperation {
        ChangeRequestVO apply(Long id);
    }
}