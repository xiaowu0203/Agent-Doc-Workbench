package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.AgentConvertor;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.ModelStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.dto.AgentCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.param.AgentSearchParam;
import com.agentdoc.agent.pojo.vo.AgentCardVO;
import com.agentdoc.agent.pojo.vo.AgentVO;
import com.agentdoc.common.constant.WorkbenchSearchConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.WorkbenchSearchQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentTaskOptionVO;
import com.agentdoc.common.feign.vo.WorkbenchSearchGroupVO;
import com.agentdoc.common.feign.vo.WorkbenchSearchItemVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;
import static com.agentdoc.common.enums.WorkbenchSearchType.AGENT;

/**
 * Agent配置管理服务
 * <p>
 * 负责Agent的增删改查、空间权限校验、模型可用性校验；
 * 对外提供创建、列表、详情、更新、删除、执行配置概要查询；
 * 权限通过统一空间权限服务校验；
 * 更新时由{@link AgentConvertor}维护 configVersion 配置版本号。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;
    /** 模型服务，用于校验模型是否启用 */
    private final ModelService modelService;
    /** 空间权限校验服务 */
    private final SpaceAccessService spaceAccessService;
    /** Agent 卡片关联摘要批量查询服务 */
    private final AgentCardSummaryService cardSummaryService;

    /**
     * 创建Agent配置
     * <p>权限要求：空间OWNER；校验目标模型必须为启用状态；填充创建人，入库后返回VO。</p>
     *
     * @param dto Agent创建入参DTO
     * @return 新建完成AgentVO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentVO create(AgentCreateDTO dto) {
        // 校验当前用户具备该空间OWNER权限
        spaceAccessService.requirePermission(dto.spaceId(), AGENT_MANAGE);
        // 校验引用的大模型必须处于启用状态
        modelService.requireEnabled(dto.modelId());
        validateSkillSelection(dto.skillSelectionMode(), dto.skillRouterModelId(), dto.modelId());
        AgentEntity entity = AgentConvertor.toEntity(dto, AuthUtils.getUserIdOrException());
        agentMapper.insert(entity);
        return AgentConvertor.toVO(entity);
    }

    /**
     * 查询指定空间下Agent列表，按创建时间倒序
     * <p>权限要求：空间VIEWER及以上。</p>
     *
     * @param spaceId 空间ID
     * @return AgentVO列表
     */
    public List<AgentVO> list(Long spaceId) {
        // 校验当前用户具备该空间VIEWER权限
        spaceAccessService.requirePermission(spaceId, AGENT_READ);
        return agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getSpaceId, spaceId)
                        .orderByDesc(AgentEntity::getCreatedAt))
                .stream().map(AgentConvertor::toVO).toList();
    }

    /**
     * 分页查询Agent卡片列表
     * 卡片关联的摘要统计只针对当前分页数据批量加载，避免N+1循环查询数据库
     *
     * @param param 查询分页参数：空间ID、状态、模型ID、关键词、分页页码页大小
     * @return Agent卡片分页结果VO
     */
    public PageVO<AgentCardVO> search(AgentSearchParam param) {
        // 参数合法性校验
        param.validate();

        // 校验当前用户拥有该空间Agent读取权限
        spaceAccessService.requirePermission(param.getSpaceId(), AGENT_READ);

        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getSpaceId, param.getSpaceId())
                .orderByDesc(AgentEntity::getUpdatedAt)
                .orderByDesc(AgentEntity::getId);
        if (param.getStatus() != null) {
            wrapper.eq(AgentEntity::getStatus, param.getStatus());
        }
        if (param.getModelId() != null) {
            wrapper.eq(AgentEntity::getModelId, param.getModelId());
        }
        // 关键词模糊搜索：匹配名称 OR 描述
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            wrapper.and(query -> query.like(AgentEntity::getName, keyword)
                    .or().like(AgentEntity::getDescription, keyword));
        }

        // 执行分页查询Agent主数据
        Page<AgentEntity> page = agentMapper.selectPage(PageUtils.toPage(param), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageVO.of(List.of(), page.getTotal(), param);
        }

        // 批量查询当前页Agent绑定的模型信息，构建id->模型映射，避免循环查询
        Map<Long, ModelEntity> models = modelService.findByIds(page.getRecords().stream()
                        .map(AgentEntity::getModelId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ModelEntity::getId, Function.identity()));
        // 批量统计当前页Agent卡片摘要（Skill、MCP、工具数量）
        Map<Long, AgentCardSummaryService.CardSummary> summaries = cardSummaryService.summarize(
                page.getRecords().stream().map(AgentEntity::getId).toList());

        // 组装Agent卡片VO，模型为空则展示null，摘要为空使用默认0值
        List<AgentCardVO> records = page.getRecords().stream().map(agent -> {
            ModelEntity model = models.get(agent.getModelId());
            AgentCardSummaryService.CardSummary summary = summaries.getOrDefault(agent.getId(),
                    new AgentCardSummaryService.CardSummary(0, 0, 0));
            return AgentConvertor.toCardVO(agent, model == null ? null : model.getDisplayName(),
                    summary.skillCount(), summary.mcpCount(), summary.toolCount());
        }).toList();
        return PageVO.of(records, page.getTotal(), param);
    }

    /**
     * 查询工作台全局搜索中的 Agent 结果。
     *
     * @param request 已规范化的工作台搜索条件
     * @return Agent 搜索分组
     */
    public WorkbenchSearchGroupVO searchWorkbench(WorkbenchSearchQueryDTO request) {
        if (request == null || request.spaceId() == null || request.keyword() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "空间和搜索关键词不能为空");
        }
        String keyword = request.keyword().trim();
        if (keyword.length() < WorkbenchSearchConstant.MIN_KEYWORD_LENGTH
                || keyword.length() > WorkbenchSearchConstant.MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "搜索关键词长度必须为 2-100 个字符");
        }
        spaceAccessService.requirePermission(request.spaceId(), AGENT_READ);
        int requestedLimit = request.limitPerType() == null
                ? WorkbenchSearchConstant.DEFAULT_LIMIT_PER_TYPE : request.limitPerType();
        int limit = Math.max(1, Math.min(requestedLimit, WorkbenchSearchConstant.MAX_LIMIT_PER_TYPE));
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getSpaceId, request.spaceId())
                .and(query -> query.like(AgentEntity::getName, keyword)
                        .or().like(AgentEntity::getDescription, keyword))
                .orderByDesc(AgentEntity::getUpdatedAt)
                .orderByDesc(AgentEntity::getId);
        Page<AgentEntity> page = agentMapper.selectPage(new Page<>(1, limit), wrapper);
        List<WorkbenchSearchItemVO> records = page.getRecords().stream()
                .map(agent -> new WorkbenchSearchItemVO(AGENT, agent.getId(), agent.getName(),
                        agent.getDescription(), AgentStatus.fromCode(agent.getStatus()).name(), agent.getUpdatedAt()))
                .toList();
        return new WorkbenchSearchGroupVO(records, page.getTotal());
    }

    /**
     * 获取Agent详情
     * <p>权限要求：空间VIEWER及以上。</p>
     *
     * @param id Agent主键ID
     * @return AgentVO
     */
    public AgentVO detail(Long id) {
        // 根据AgentId获取智能体信息
        AgentEntity entity = require(id);
        // 校验当前用户具备该空间VIEWER权限
        spaceAccessService.requirePermission(entity.getSpaceId(), AGENT_READ);
        return AgentConvertor.toVO(entity);
    }

    /**
     * 更新Agent配置
     * <p>权限要求：空间 OWNER；校验模型启用；更新自动递增 configVersion。</p>
     *
     * @param id  Agent主键ID
     * @param dto Agent更新入参DTO
     * @return 更新后AgentVO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentVO update(Long id, AgentUpdateDTO dto) {
        // 根据AgentId获取智能体信息
        AgentEntity entity = require(id);
        // 校验当前用户具备该空间OWNER权限
        spaceAccessService.requirePermission(entity.getSpaceId(), AGENT_MANAGE);
        // 校验引用的大模型必须处于启用状态
        modelService.requireEnabled(dto.modelId());
        validateSkillSelection(dto.skillSelectionMode(), dto.skillRouterModelId(), dto.modelId());
        // 更新Agent信息
        AgentConvertor.apply(entity, dto);
        agentMapper.updateById(entity);
        return AgentConvertor.toVO(entity);
    }

    /**
     * 删除Agent配置
     * <p>权限要求：空间OWNER。</p>
     *
     * @param id Agent主键ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 根据AgentId获取智能体信息
        AgentEntity entity = require(id);
        // 校验当前用户具备该空间OWNER权限
        spaceAccessService.requirePermission(entity.getSpaceId(), AGENT_MANAGE);
        agentMapper.deleteById(id);
    }

    /**
     * 获取Agent实体，不存在则抛出业务异常
     *
     * @param id Agent主键ID
     * @return AgentEntity数据库实体
     */
    public AgentEntity require(Long id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return entity;
    }

    /**
     * 根据ID查询Agent信息（FOR UPDATE）
     *
     * @param id Agent 主键 ID
     * @return 已锁定的 Agent 实体
     */
    public AgentEntity requireForUpdate(Long id) {
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getId, id)
                .last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return entity;
    }

    /**
     * 更新 Agent 信息
     *
     * @param entity Agent 实体
     */
    public void updateConfiguration(AgentEntity entity) {
        agentMapper.updateById(entity);
    }

    /**
     * 获取Agent运行执行概要配置
     * <p>
     * 给Agent运行时使用，返回执行需要的关键参数：模型、token预算、文档范围、版本、价格等；
     * A2A任务启动时会调用此接口拿到运行时配置。
     * </p>
     *
     * @param id Agent主键ID
     * @return Agent执行配置概要VO
     */
    public AgentExecutionProfileVO executionProfile(Long id) {
        AgentEntity agent = require(id);
        ModelEntity model = modelService.requireEnabled(agent.getModelId());
        return new AgentExecutionProfileVO(
                agent.getId(), agent.getSpaceId(), agent.getModelId(), agent.getTokenBudget(), agent.getDocScope(),
                agent.getConfigVersion(), AgentStatus.ENABLED.matches(agent.getStatus()), model.getInputPricePerMillion(),
                model.getOutputPricePerMillion());
    }

    /**
     * 批量获取Agent引用基础信息，用于任务列表回填展示
     * 只返回id、spaceId、name极简字段，不返回完整Agent配置
     *
     * @param agentIds Agent ID集合
     * @return Agent引用VO列表
     */
    public List<AgentRefVO> listRefs(Collection<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return List.of();
        }
        // 批量查询Agent，映射为轻量引用对象
        return agentMapper.selectBatchIds(agentIds).stream()
                .map(agent -> new AgentRefVO(agent.getId(), agent.getSpaceId(), agent.getName()))
                .toList();
    }

    /**
     * 查询空间内可用于创建任务的Agent选项列表
     * 筛选条件：Agent启用状态、模型可用、Agent文档作用域允许访问目标文档
     * 注意：本方法**不做权限校验**，仅供task‑service调用，调用方必须先完成用户权限、文档归属校验
     *
     * @param spaceId 空间ID
     * @param documentId 目标文档ID
     * @return 可选用Agent任务选项VO列表
     */
    public List<AgentTaskOptionVO> listTaskOptions(Long spaceId, Long documentId) {
        // 查询空间下所有启用状态Agent，按更新时间、ID倒序
        List<AgentEntity> agents = agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getSpaceId, spaceId)
                        .eq(AgentEntity::getStatus, AgentStatus.ENABLED.getCode())
                        .orderByDesc(AgentEntity::getUpdatedAt)
                        .orderByDesc(AgentEntity::getId))
                .stream()
                // 过滤：Agent文档作用域允许访问该文档
                .filter(agent -> allowsDocument(agent.getDocScope(), documentId))
                .toList();
        if (agents.isEmpty()) {
            return List.of();
        }

        // 批量查询Agent绑定的模型实体
        Map<Long, ModelEntity> models = modelService.findByIds(agents.stream()
                        .map(AgentEntity::getModelId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ModelEntity::getId, Function.identity()));

        // 二次过滤：模型必须为启用状态，排除模型不可用的Agent
        List<AgentEntity> availableAgents = agents.stream()
                .filter(agent -> {
                    ModelEntity model = models.get(agent.getModelId());
                    return model != null && ModelStatus.ENABLED.matches(model.getStatus());
                })
                .toList();

        // 批量统计可用Agent的卡片摘要信息
        Map<Long, AgentCardSummaryService.CardSummary> summaries = cardSummaryService.summarize(
                availableAgents.stream().map(AgentEntity::getId).toList());

        // 组装任务选项VO
        return availableAgents.stream().map(agent -> {
            ModelEntity model = models.get(agent.getModelId());
            AgentCardSummaryService.CardSummary summary = summaries.getOrDefault(agent.getId(),
                    new AgentCardSummaryService.CardSummary(0, 0, 0));
            return new AgentTaskOptionVO(agent.getId(), agent.getName(), model.getDisplayName(),
                    agent.getSkillSelectionMode(), agent.getTokenBudget(), agent.getExecutionTimeoutSeconds(),
                    summary.skillCount(), summary.mcpCount());
        }).toList();
    }

    /**
     * 判断Agent的文档作用域是否允许访问指定文档
     * 兼容两种scope格式：
     * 1. 直接数组格式：[1,2,3]
     * 2. 对象格式：{"documentIds":[1,2,3]}
     * 若docScope为空/空白，代表无文档限制，全部允许访问
     *
     * @param documentScope Agent的文档作用域JSON字符串
     * @param documentId 待校验文档ID
     * @return true允许访问，false不允许
     */
    private boolean allowsDocument(String documentScope, Long documentId) {
        // 作用域为空，无文档限制，直接放行
        if (documentScope == null || documentScope.isBlank()) {
            return true;
        }
        // 尝试解析为直接文档ID数组
        List<Long> directIds = JsonUtils.parse(documentScope, new TypeReference<List<Long>>() { });
        if (directIds != null) {
            return directIds.contains(documentId);
        }
        // 尝试解析为对象格式，取documentIds字段
        Map<String, List<Long>> scope = JsonUtils.parse(documentScope,
                new TypeReference<Map<String, List<Long>>>() { });
        return scope != null && scope.get("documentIds") != null && scope.get("documentIds").contains(documentId);
    }

    /**
     * 校验Skill路由配置是否合法
     * @param mode 路由类型
     * @param routerModelId 绑定的路由模型ID
     * @param mainModelId 主模型ID
     */
    private void validateSkillSelection(SkillSelectionMode mode, Long routerModelId, Long mainModelId) {
        if (mode == SkillSelectionMode.ALL_BOUND && routerModelId != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "ALL_BOUND 模式不能配置 Skill Router 模型");
        }
        if (mode == SkillSelectionMode.ROUTER && routerModelId != null && !routerModelId.equals(mainModelId)) {
            modelService.requireEnabled(routerModelId);
        }
    }
}
