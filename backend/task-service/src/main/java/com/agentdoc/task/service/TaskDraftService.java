package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.mapper.TaskDraftMapper;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.dto.TaskDraftSaveDTO;
import com.agentdoc.task.pojo.dto.TaskFocusRegionDTO;
import com.agentdoc.task.pojo.entity.TaskDraftEntity;
import com.agentdoc.task.pojo.param.TaskDraftSearchParam;
import com.agentdoc.task.pojo.vo.TaskDraftVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_CREATE;

/**
 * 当前用户的任务表单草稿服务。
 */
@Service
@RequiredArgsConstructor
public class TaskDraftService {

    private final TaskDraftMapper taskDraftMapper;
    private final TaskService taskService;
    private final DocumentFeign documentFeign;
    private final AgentFeign agentFeign;

    public TaskDraftVO create(TaskDraftSaveDTO dto) {
        Long userId = AuthUtils.getUserIdOrException();
        requirePermission(dto.spaceId());
        validateReferences(dto);
        TaskDraftEntity entity = new TaskDraftEntity();
        apply(entity, dto);
        entity.setCreatedBy(userId);
        taskDraftMapper.insert(entity);
        return TaskDraftVO.from(entity);
    }

    public TaskDraftVO update(Long id, TaskDraftSaveDTO dto) {
        TaskDraftEntity entity = requireOwned(id);
        if (!entity.getSpaceId().equals(dto.spaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务草稿所属空间不能修改");
        }
        validateReferences(dto);
        apply(entity, dto);
        taskDraftMapper.updateById(entity);
        return TaskDraftVO.from(entity);
    }

    public TaskDraftVO detail(Long id) {
        return TaskDraftVO.from(requireOwned(id));
    }

    public PageVO<TaskDraftVO> search(TaskDraftSearchParam param) {
        Long userId = AuthUtils.getUserIdOrException();
        requirePermission(param.getSpaceId());
        param.validate();
        LambdaQueryWrapper<TaskDraftEntity> wrapper = new LambdaQueryWrapper<TaskDraftEntity>()
                .eq(TaskDraftEntity::getSpaceId, param.getSpaceId())
                .eq(TaskDraftEntity::getCreatedBy, userId)
                .orderByDesc(TaskDraftEntity::getUpdatedAt)
                .orderByDesc(TaskDraftEntity::getId);
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            wrapper.and(query -> query.like(TaskDraftEntity::getName, keyword)
                    .or().like(TaskDraftEntity::getInstruction, keyword));
        }
        Page<TaskDraftEntity> page = taskDraftMapper.selectPage(
                new Page<>(param.getPageNum(), param.getPageSize()), wrapper);
        return PageVO.of(page.getRecords().stream().map(TaskDraftVO::from).toList(), page.getTotal(), param);
    }

    public void delete(Long id) {
        TaskDraftEntity entity = requireOwned(id);
        taskDraftMapper.deleteById(entity.getId());
    }

    /**
     * 校验草稿完整性并启动正式任务；任务创建成功后移除草稿。
     */
    public TaskVO launch(Long id) {
        TaskDraftEntity entity = requireOwned(id);
        if (entity.getAgentId() == null || entity.getDocumentId() == null
                || entity.getName() == null || entity.getName().isBlank()
                || entity.getInstruction() == null || entity.getInstruction().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务草稿信息不完整，无法启动");
        }
        TaskReadScope readScope = entity.getReadScope() == null
                ? TaskReadScope.FULL : TaskReadScope.valueOf(entity.getReadScope());
        List<TaskFocusRegionDTO> regions = readRegions(entity.getFocusRegionsJson());
        if (readScope == TaskReadScope.RANGES && regions.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务草稿尚未选择读取区域");
        }
        TaskVO task = taskService.create(new TaskCreateDTO(
                entity.getSpaceId(), entity.getAgentId(), entity.getDocumentId(), entity.getName(),
                entity.getInstruction(), entity.getTokenBudget(), readScope, regions));
        taskDraftMapper.deleteById(entity.getId());
        return task;
    }

    private TaskDraftEntity requireOwned(Long id) {
        TaskDraftEntity entity = taskDraftMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务草稿不存在");
        }
        Long userId = AuthUtils.getUserIdOrException();
        if (!Objects.equals(entity.getCreatedBy(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该任务草稿");
        }
        requirePermission(entity.getSpaceId());
        return entity;
    }

    private void validateReferences(TaskDraftSaveDTO dto) {
        DocumentExecutionContextVO document = null;
        if (dto.documentId() != null) {
            document = requireData(documentFeign.getExecutionContext(dto.documentId()));
            if (!dto.spaceId().equals(document.spaceId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "任务草稿与目标文档不属于同一空间");
            }
            if (!document.normal()) {
                throw new BusinessException(ErrorCode.CONFLICT, "已归档文档不能保存到任务草稿");
            }
        }
        if (dto.agentId() != null) {
            AgentExecutionProfileVO agent = requireData(agentFeign.getExecutionProfile(dto.agentId()));
            if (!dto.spaceId().equals(agent.spaceId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "任务草稿与 Agent 不属于同一空间");
            }
        }
        validateDraftRegions(dto, document);
    }

    private void validateDraftRegions(TaskDraftSaveDTO dto, DocumentExecutionContextVO document) {
        if (dto.focusRegions() == null || document == null) {
            return;
        }
        long documentLength = document.contentLength() == null ? 0L : document.contentLength();
        for (TaskFocusRegionDTO region : dto.focusRegions()) {
            long end;
            try {
                end = Math.addExact(region.start(), region.length());
            } catch (ArithmeticException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关注区域超出有效范围");
            }
            if (region.start() >= documentLength || end > documentLength) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关注区域超出文档长度");
            }
        }
    }

    private void apply(TaskDraftEntity entity, TaskDraftSaveDTO dto) {
        TaskReadScope scope = dto.readScope() == null ? TaskReadScope.FULL : dto.readScope();
        entity.setSpaceId(dto.spaceId());
        entity.setAgentId(dto.agentId());
        entity.setDocumentId(dto.documentId());
        entity.setName(trimToNull(dto.name()));
        entity.setInstruction(trimToNull(dto.instruction()));
        entity.setTokenBudget(dto.tokenBudget());
        entity.setReadScope(scope.name());
        entity.setFocusRegionsJson(dto.focusRegions() == null || dto.focusRegions().isEmpty()
                ? null : JsonUtils.toJson(dto.focusRegions()));
    }

    private List<TaskFocusRegionDTO> readRegions(String json) {
        List<TaskFocusRegionDTO> regions = JsonUtils.parse(json,
                new TypeReference<List<TaskFocusRegionDTO>>() { });
        return regions == null ? List.of() : regions;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requirePermission(Long spaceId) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, TASK_CREATE);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "文档服务权限校验失败" : result.message());
        }
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}
