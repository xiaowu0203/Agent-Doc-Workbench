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
 * <p>提供任务草稿的新增、修改、查询、删除、启动任务能力；
 * 草稿属于用户私有，只能本人操作；草稿保存时会校验文档、Agent、关注区域合法性，
 * 启动草稿会校验完整性，创建正式任务后自动删除草稿。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskDraftService {

    private final TaskDraftMapper taskDraftMapper;
    private final TaskService taskService;
    private final DocumentFeign documentFeign;
    private final AgentFeign agentFeign;

    /**
     * 创建任务草稿
     * @param dto 草稿保存入参
     * @return 新建草稿VO
     */
    public TaskDraftVO create(TaskDraftSaveDTO dto) {
        // 获取当前登录用户ID
        Long userId = AuthUtils.getUserIdOrException();
        // 校验空间拥有任务创建权限
        requirePermission(dto.spaceId());
        // 校验引用的文档、Agent、关注区域合法性
        validateReferences(dto);

        TaskDraftEntity entity = new TaskDraftEntity();
        // 将dto字段赋值到实体
        apply(entity, dto);
        entity.setCreatedBy(userId);
        taskDraftMapper.insert(entity);
        return TaskDraftVO.from(entity);
    }

    /**
     * 更新已有任务草稿
     * @param id 草稿ID
     * @param dto 更新入参
     * @return 更新后的草稿VO
     */
    public TaskDraftVO update(Long id, TaskDraftSaveDTO dto) {
        // 获取草稿，校验归属和权限
        TaskDraftEntity entity = requireOwned(id);
        // 禁止修改草稿所属空间
        if (!entity.getSpaceId().equals(dto.spaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务草稿所属空间不能修改");
        }
        validateReferences(dto);
        apply(entity, dto);
        taskDraftMapper.updateById(entity);
        return TaskDraftVO.from(entity);
    }

    /**
     * 查询草稿详情
     * @param id 草稿ID
     * @return 草稿详情VO
     */
    public TaskDraftVO detail(Long id) {
        return TaskDraftVO.from(requireOwned(id));
    }

    /**
     * 分页搜索当前用户的任务草稿
     * <p>按更新时间倒序，支持名称、指令关键词模糊搜索</p>
     * @param param 搜索参数：空间ID、分页、关键词
     * @return 分页草稿结果
     */
    public PageVO<TaskDraftVO> search(TaskDraftSearchParam param) {
        Long userId = AuthUtils.getUserIdOrException();
        requirePermission(param.getSpaceId());
        param.validate();

        LambdaQueryWrapper<TaskDraftEntity> wrapper = new LambdaQueryWrapper<TaskDraftEntity>()
                .eq(TaskDraftEntity::getSpaceId, param.getSpaceId())
                .eq(TaskDraftEntity::getCreatedBy, userId)
                .orderByDesc(TaskDraftEntity::getUpdatedAt)
                .orderByDesc(TaskDraftEntity::getId);

        // 关键词模糊匹配：名称 OR 指令
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            wrapper.and(query -> query.like(TaskDraftEntity::getName, keyword)
                    .or().like(TaskDraftEntity::getInstruction, keyword));
        }

        Page<TaskDraftEntity> page = taskDraftMapper.selectPage(
                new Page<>(param.getPageNum(), param.getPageSize()), wrapper);
        return PageVO.of(page.getRecords().stream().map(TaskDraftVO::from).toList(), page.getTotal(), param);
    }

    /**
     * 删除任务草稿
     * @param id 草稿ID
     */
    public void delete(Long id) {
        TaskDraftEntity entity = requireOwned(id);
        taskDraftMapper.deleteById(entity.getId());
    }

    /**
     * 校验草稿完整性并启动正式任务；任务创建成功后移除草稿。
     * <p>校验必填字段、读取范围；如果是RANGES范围读取，必须配置关注区域；
     * 调用taskService创建正式任务，成功后删除草稿记录。</p>
     * @param id 草稿ID
     * @return 创建完成的正式任务VO
     */
    public TaskVO launch(Long id) {
        TaskDraftEntity entity = requireOwned(id);

        // 校验草稿必填字段完整性
        if (entity.getAgentId() == null || entity.getDocumentId() == null
                || entity.getName() == null || entity.getName().isBlank()
                || entity.getInstruction() == null || entity.getInstruction().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务草稿信息不完整，无法启动");
        }

        // 读取范围，为空默认 FULL
        TaskReadScope readScope = entity.getReadScope() == null
                ? TaskReadScope.FULL : TaskReadScope.valueOf(entity.getReadScope());
        // 反序列化关注区域json
        List<TaskFocusRegionDTO> regions = readRegions(entity.getFocusRegionsJson());

        // 如果是范围读取模式，必须存在关注区域
        if (readScope == TaskReadScope.RANGES && regions.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "任务草稿尚未选择读取区域");
        }

        // 使用草稿数据创建正式任务
        TaskVO task = taskService.create(new TaskCreateDTO(
                entity.getSpaceId(), entity.getAgentId(), entity.getDocumentId(), entity.getName(),
                entity.getInstruction(), entity.getTokenBudget(), readScope, regions));

        // 任务创建成功，删除草稿
        taskDraftMapper.deleteById(entity.getId());
        return task;
    }

    /**
     * 获取草稿实体，做归属校验、权限校验
     * <ul>
     * <li>草稿不存在抛404</li>
     * <li>非创建用户访问抛无权</li>
     * <li>校验空间任务创建权限</li>
     * </ul>
     * @param id 草稿ID
     * @return 草稿实体
     */
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

    /**
     * 校验草稿引用资源合法性
     * <ul>
     * <li>文档：必须同空间、文档不能归档</li>
     * <li>Agent：必须同空间</li>
     * <li>校验关注区域是否越界</li>
     * </ul>
     * @param dto 草稿保存DTO
     */
    private void validateReferences(TaskDraftSaveDTO dto) {
        DocumentExecutionContextVO document = null;
        if (dto.documentId() != null) {
            document = requireData(documentFeign.getExecutionContext(dto.documentId()));
            // 文档与草稿必须属于同一个空间
            if (!dto.spaceId().equals(document.spaceId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "任务草稿与目标文档不属于同一空间");
            }
            // 归档文档不允许保存草稿
            if (!document.normal()) {
                throw new BusinessException(ErrorCode.CONFLICT, "已归档文档不能保存到任务草稿");
            }
        }
        if (dto.agentId() != null) {
            AgentExecutionProfileVO agent = requireData(agentFeign.getExecutionProfile(dto.agentId()));
            // Agent与草稿必须属于同一个空间
            if (!dto.spaceId().equals(agent.spaceId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "任务草稿与 Agent 不属于同一空间");
            }
        }
        validateDraftRegions(dto, document);
    }

    /**
     * 校验草稿的关注区域是否超出文档实际长度
     * @param dto 草稿保存DTO
     * @param document 文档上下文信息
     */
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

    /**
     * 将DTO字段赋值到草稿实体
     * <p>关注区域对象序列化为JSON字符串存储数据库</p>
     * @param entity 草稿实体
     * @param dto 保存DTO
     */
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

    /**
     * 反序列化数据库中存储的关注区域JSON
     * @param json 存储的json字符串
     * @return 关注区域列表，null返回空列表
     */
    private List<TaskFocusRegionDTO> readRegions(String json) {
        List<TaskFocusRegionDTO> regions = JsonUtils.parse(json,
                new TypeReference<List<TaskFocusRegionDTO>>() { });
        return regions == null ? List.of() : regions;
    }

    /**
     * 字符串trim，空白字符串转为null，用于数据库存储
     * @param value 原始字符串
     * @return 处理后字符串
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 校验当前用户在指定空间拥有TASK_CREATE任务创建权限
     * @param spaceId 空间ID
     */
    private void requirePermission(Long spaceId) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, TASK_CREATE);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "文档服务权限校验失败" : result.message());
        }
    }

    /**
     * Feign远程调用结果断言工具
     * 校验返回成功且data不为null，失败抛出业务异常
     * @param result 远程返回结果
     * @return 业务数据
     * @param <T> 返回泛型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "远程服务调用失败" : result.message());
        }
        return result.data();
    }
}