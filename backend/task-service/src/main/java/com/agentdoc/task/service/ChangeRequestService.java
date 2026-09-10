package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.convertor.ChangeRequestConvertor;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_SUBMIT;

/** 变更请求提交与基础读取服务；查询聚合和审批状态机由专用服务负责。 */
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
        ChangeRequestEntity entity = ChangeRequestConvertor.fromHumanSubmission(dto, userId, document.spaceId());
        changeRequestMapper.insert(entity);
        return ChangeRequestConvertor.toVO(entity, document.title());
    }

    /**
     * Agent 任务内部提交正式文档变更，不依赖 MQ 线程中的用户 SecurityContext。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestEntity submitFromAgent(TaskEntity task, List<ChangeItemDTO> changes, Long baseVersion) {
        return submitFromAgent(task, changes, baseVersion, null);
    }

    /** Agent 任务内部提交正式文档变更，并保存变更摘要。 */
    @Transactional(rollbackFor = Exception.class)
    public ChangeRequestEntity submitFromAgent(TaskEntity task, List<ChangeItemDTO> changes,
                                               Long baseVersion, String summary) {
        ChangeRequestEntity previous = changeRequestMapper.selectOne(new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getReworkTaskId, task.getId())
                .last("LIMIT 1"));
        Long parentRequestId = previous == null ? null : previous.getId();
        Integer revisionNo = previous == null || previous.getRevisionNo() == null
                ? 1 : previous.getRevisionNo() + 1;
        ChangeRequestEntity entity = ChangeRequestConvertor.fromAgentSubmission(
                task, changes, baseVersion, summary, parentRequestId, revisionNo);
        changeRequestMapper.insert(entity);
        return entity;
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
     * 校验远程调用 Result：成功返回 data，业务失败抛对应业务异常。
     * @param result 远程调用返回的 Result（契约统一 Result 封装）
     * @param <T> data 类型
     * @return Result.data
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }

    /**
     * 根据文档ID查询文档信息，不存在抛出异常
     * @param documentId 文档Id
     * @return 文档信息
     */
    private DocumentRefVO requireDocumentRef(Long documentId) {
        List<DocumentRefVO> documents = requireData(documentFeign.getDocumentRefs(List.of(documentId)));
        DocumentRefVO document = documents.stream()
                .filter(item -> item.id().equals(documentId))
                .findFirst()
                .orElse(null);
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
