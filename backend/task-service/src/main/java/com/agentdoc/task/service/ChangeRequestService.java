package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.PageUtils;
import com.agentdoc.task.convertor.ChangeRequestConvertor;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.dto.ChangeRequestReviewDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.param.ChangeRequestSearchParam;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import com.agentdoc.task.pojo.vo.PendingChangeStatsVO;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_APPROVE;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_MERGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_SUBMIT;

/**
 * 变更请求服务（审批队列）。
 * <p>状态机：PENDING → APPROVED → MERGED；PENDING → REJECTED / RETURNED。
 * 提交 / 审批要求登录用户（{@code @RequireLogin}），空间角色校验（EDITOR 及以上）
 * 待 Phase 3 引入权限校验 Feign 接口后补齐，本阶段以登录为门槛。</p>
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestService {

    private final ChangeRequestMapper changeRequestMapper;
    private final DocumentFeign documentFeign;

    /**
     * 提交变更请求（进入审批队列）。
     * @param dto 提交请求
     * @return 变更请求视图
     */
    public ChangeRequestVO submit(ChangeRequestSubmitDTO dto) {
        Long userId = AuthUtils.getUserIdOrException();
        // 校验文档是否存在
        DocumentRefVO document = requireDocumentRef(dto.documentId());
        // 校验用户是否拥有该空间的提交权限
        requireSpacePermission(document.spaceId(), CHANGE_REQUEST_SUBMIT);
        ChangeRequestEntity entity = ChangeRequestConvertor.fromHumanSubmission(dto, userId);
        changeRequestMapper.insert(entity);
        return ChangeRequestConvertor.toVO(entity, document.title());
    }

    /**
     * Agent 任务内部提交正式文档变更，不依赖 MQ 线程中的用户 SecurityContext。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestEntity submitFromAgent(TaskEntity task, List<ChangeItemDTO> changes, Long baseVersion) {
        ChangeRequestEntity entity = ChangeRequestConvertor.fromAgentSubmission(task, changes, baseVersion);
        changeRequestMapper.insert(entity);
        return entity;
    }

    /**
     * 审批队列查询（可按空间 / 文档 / 状态过滤，分页，按创建时间倒序）。
     * @param param 查询参数（过滤条件 + 分页）
     * @return 分页变更请求列表
     */
    public PageVO<ChangeRequestVO> list(ChangeRequestSearchParam param) {
        // 获取空间Id
        Long spaceId = param.spaceId();
        // 校验用户是否拥有该空间的查询权限
        requireSpacePermission(spaceId, CHANGE_REQUEST_READ);
        // 获取文档Id
        Long documentId = param.documentId();
        // 获取状态
        ChangeRequestStatus status = param.status();
        PageParam pageParam = param.pageParam();
        LambdaQueryWrapper<ChangeRequestEntity> wrapper = new LambdaQueryWrapper<>();
        if (documentId != null) {
            wrapper.eq(ChangeRequestEntity::getDocumentId, documentId);
        }
        if (status != null) {
            wrapper.eq(ChangeRequestEntity::getStatus, status.getCode());
        }
        if (spaceId != null) {
            // 根据空间Id获取文档Id列表
            List<Long> docIds = requireData(documentFeign.listDocumentIdsBySpace(spaceId));
            if (docIds.isEmpty()) {
                return PageVO.of(List.of(), 0, pageParam);
            }
            wrapper.in(ChangeRequestEntity::getDocumentId, docIds);
        }
        // 按照创建时间降序排序
        wrapper.orderByDesc(ChangeRequestEntity::getCreatedAt);
        // 执行查询
        Page<ChangeRequestEntity> page = changeRequestMapper.selectPage(PageUtils.toPage(pageParam), wrapper);
        // 批量：一次 Feign 查询回填全部文档标题，批量转换收敛在实体 toVOList
        List<ChangeRequestEntity> records = page.getRecords();
        Map<Long, DocumentRefVO> refs = fetchRefs(records.stream()
                .map(ChangeRequestEntity::getDocumentId).distinct().toList());
        List<ChangeRequestVO> changeRequestVOList = ChangeRequestConvertor.toVOList(records, refs);
        return PageVO.of(changeRequestVOList, page.getTotal(), pageParam);
    }

    /**
     * 查询空间当前待审批变更数与截至昨日的数量。
     * 变更表通过文档 ID 归属空间，因此先批量读取空间文档 ID，再统计变更记录。
     *
     * @param spaceId 空间 ID
     * @return 两个原始数量，差值由前端计算
     */
    public PendingChangeStatsVO getStats(Long spaceId) {
        requireSpacePermission(spaceId, CHANGE_REQUEST_READ);
        List<Long> documentIds = requireData(documentFeign.listDocumentIdsBySpace(spaceId));
        if (documentIds.isEmpty()) {
            return new PendingChangeStatsVO(0, 0);
        }
        LocalDateTime yesterdayStart = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<ChangeRequestEntity> current = new LambdaQueryWrapper<ChangeRequestEntity>()
                .in(ChangeRequestEntity::getDocumentId, documentIds)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode());
        long pendingCount = changeRequestMapper.selectCount(current);
        LambdaQueryWrapper<ChangeRequestEntity> historical = new LambdaQueryWrapper<ChangeRequestEntity>()
                .in(ChangeRequestEntity::getDocumentId, documentIds)
                .eq(ChangeRequestEntity::getStatus, ChangeRequestStatus.PENDING.getCode())
                .lt(ChangeRequestEntity::getCreatedAt, yesterdayStart);
        long pendingCountAsOfYesterday = changeRequestMapper.selectCount(historical);
        return new PendingChangeStatsVO(pendingCount, pendingCountAsOfYesterday);
    }

    /**
     * 审批通过（PENDING → APPROVED，待合并）。
     * @param id 变更请求 ID
     * @param dto 审批意见
     * @return 变更请求视图
     */
    public ChangeRequestVO approve(Long id, ChangeRequestReviewDTO dto) {
        return transition(id, ChangeRequestStatus.PENDING, ChangeRequestStatus.APPROVED, dto.reviewComment());
    }

    /**
     * 审批拒绝（PENDING → REJECTED）。
     * @param id 变更请求 ID
     * @param dto 拒绝意见
     * @return 变更请求视图
     */
    public ChangeRequestVO reject(Long id, ChangeRequestReviewDTO dto) {
        return transition(id, ChangeRequestStatus.PENDING, ChangeRequestStatus.REJECTED, dto.reviewComment());
    }

    /**
     * 批注退回（PENDING → RETURNED，要求 Agent 重改）。
     * @param id 变更请求 ID
     * @param dto 退回批注
     * @return 变更请求视图
     */
    public ChangeRequestVO returnRequest(Long id, ChangeRequestReviewDTO dto) {
        return transition(id, ChangeRequestStatus.PENDING, ChangeRequestStatus.RETURNED, dto.reviewComment());
    }

    /**
     * 合并变更至正式文档（APPROVED → MERGED）：
     * 经 Feign 调 document-service 应用变更并生成新版本快照；基线版本不匹配（并发覆盖）时报冲突。
     * @param id 变更请求 ID
     * @return 合并后的变更请求视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestVO merge(Long id) {
        // 根据变更请求Id查询变更请求实体（不存在抛异常）
        ChangeRequestEntity entity = requireRequest(id);
        // 校验文档是否存在
        DocumentRefVO document = requireDocumentRef(entity.getDocumentId());
        // 查看用户是否具备该空间的合并权限
        requireSpacePermission(document.spaceId(), CHANGE_REQUEST_MERGE);
        // 根据变更请求状态 -> 变更请求状态枚举
        ChangeRequestStatus current = ChangeRequestStatus.fromCode(entity.getStatus());
        // 若状态非【通过】，抛出异常
        if (current != ChangeRequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已通过的变更请求可合并");
        }
        try {
            // 应用变更并合并至正式文档（契约统一 Result：业务失败走 Result.code，网络层异常抛 FeignException）
            requireData(documentFeign.mergeDocument(new MergeRequestDTO(
                    entity.getDocumentId(),
                    entity.getBaseVersion(),
                    ChangeRequestConvertor.parseChanges(entity.getChanges()),
                    "审批合并变更")));
        } catch (FeignException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "合并服务调用失败：" + e.status());
        }
        // 变更请求状态 -> 已合并
        entity.setStatus(ChangeRequestStatus.MERGED.getCode());
        // 更新变更请求
        changeRequestMapper.updateById(entity);
        String title = fetchTitle(entity.getDocumentId());
        return ChangeRequestConvertor.toVO(entity, title);
    }

    /**
     * 状态流转校验并更新。
     * @param id 变更请求 ID
     * @param from 当前状态
     * @param to 目标状态
     * @param comment 审批意见
     * @return 更新后的变更请求视图
     */
    private ChangeRequestVO transition(Long id, ChangeRequestStatus from, ChangeRequestStatus to, String comment) {
        // 根据变更请求Id查询变更请求实体（不存在抛异常）
        ChangeRequestEntity entity = requireRequest(id);
        // 校验文档是否存在
        DocumentRefVO document = requireDocumentRef(entity.getDocumentId());
        // 校验用户是否具备该空间的变更请求审批权限
        requireSpacePermission(document.spaceId(), CHANGE_REQUEST_APPROVE);
        // 根据变更请求状态 -> 变更请求状态枚举
        ChangeRequestStatus current = ChangeRequestStatus.fromCode(entity.getStatus());
        // 若【数据库记录状态】与【传入的当前状态】不一致，则抛出异常
        if (current != from) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不允许该操作（期望：" + from.getName() + "）");
        }
        // 变更请求状态 -> 目标状态
        entity.setStatus(to.getCode());
        // 变更请求审批意见
        entity.setReviewComment(comment);
        // 更新变更请求
        changeRequestMapper.updateById(entity);
        String title = fetchTitle(entity.getDocumentId());
        return ChangeRequestConvertor.toVO(entity, title);
    }

    /**
     * 按 ID 查询变更请求，不存在抛 404。
     * @param id 变更请求 ID
     * @return 变更请求实体
     */
    public ChangeRequestEntity requireRequest(Long id) {
        ChangeRequestEntity entity = changeRequestMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "变更请求不存在");
        }
        return entity;
    }

    /**
     * 经 document-service Feign 批量查询文档引用投影（标题回填；字段映射收敛在实体 toVO）。
     * @param documentIds 文档 ID 列表
     * @return 文档 ID → 引用投影
     */
    private Map<Long, DocumentRefVO> fetchRefs(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        // 根据文档Id列表查询【文档引用投影】，转为Map
        return requireData(documentFeign.getDocumentRefs(documentIds)).stream()
                .collect(Collectors.toMap(DocumentRefVO::id, Function.identity()));
    }

    /**
     * 校验远程调用 Result：成功返回 data，业务失败抛对应业务异常。
     * @param result 远程调用返回的 Result（契约统一 Result 封装）
     * @param <T> data 类型
     * @return Result.data
     */
    private <T> T requireData(Result<T> result) {
        if (result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result.code(), result.message());
        }
        return result.data();
    }

    /**
     * 查询单个文档标题（单条操作经 Feign 查一次；不存在返回 null）。
     * @param documentId 文档 ID
     * @return 文档标题或 null
     */
    private String fetchTitle(Long documentId) {
        Map<Long, DocumentRefVO> refs = fetchRefs(List.of(documentId));
        DocumentRefVO ref = refs.get(documentId);
        return ref == null ? null : ref.title();
    }

    /**
     * 根据文档ID查询文档信息，不存在抛出异常
     * @param documentId 文档Id
     * @return 文档信息
     */
    private DocumentRefVO requireDocumentRef(Long documentId) {
        DocumentRefVO document = fetchRefs(List.of(documentId)).get(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    /**
     * 校验用户是否具备该空间的【permissionCode】权限
     * @param spaceId 空间ID
     * @param permissionCode 指定的权限标识符
     */
    private void requireSpacePermission(Long spaceId, String permissionCode) {
        requireData(documentFeign.checkSpacePermission(spaceId, permissionCode));
    }
}
