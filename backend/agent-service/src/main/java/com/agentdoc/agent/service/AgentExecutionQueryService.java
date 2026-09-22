package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.AgentExecutionStatus;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentExecutionModelCallMapper;
import com.agentdoc.agent.mapper.AgentExecutionToolCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentExecutionTokenUsageBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentEvaluationEvidenceQueryDTO;
import com.agentdoc.common.feign.dto.AgentToolCallPageQueryDTO;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageBatchVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.AgentExecutionReplayIdentityVO;
import com.agentdoc.common.feign.vo.AgentEvaluationEvidenceVO;
import com.agentdoc.common.feign.vo.AgentToolCallVO;
import com.agentdoc.common.feign.vo.AgentToolSourceCountVO;
import com.agentdoc.common.feign.vo.AgentToolUsageStatsVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;

import static com.agentdoc.agent.constant.AgentConstant.TOKEN_PRICING_CURRENCY;
import static com.agentdoc.agent.constant.AgentConstant.TOKEN_PRICING_SCHEMA_VERSION;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_READ;

/**
 * Agent执行记录查询服务
 * 提供Agent执行审计、Token用量统计、工具调用统计、快照解析能力
 * 包含完整执行链路：执行记录、模型调用、工具调用审计查询
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionQueryService {

    private final AgentExecutionMapper executionMapper;
    private final AgentExecutionModelCallMapper modelCallMapper;
    private final AgentExecutionToolCallMapper toolCallMapper;
    private final SpaceAccessService spaceAccessService;

    /**
     * 根据工作台任务ID查询单次Agent完整执行审计信息
     * 包含执行本体、模型调用记录、工具调用记录，按执行序号升序排列
     *
     * @param taskId 工作台任务ID
     * @param spaceId 空间ID，用于权限校验
     * @return 执行审计VO；不存在执行记录返回null；空间不匹配抛出异常
     */
    public AgentExecutionAuditVO getByWorkbenchTask(Long taskId, Long spaceId) {
        // 校验当前用户拥有该空间任务读取权限
        spaceAccessService.requirePermission(spaceId, TASK_READ);

        // 根据工作台任务ID查询Agent执行主记录
        AgentExecutionEntity execution = executionMapper.selectOne(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));

        // 无执行记录直接返回null
        if (execution == null) {
            return null;
        }

        // 校验执行记录所属空间与入参空间一致，防止越权访问
        if (!spaceId.equals(execution.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 执行记录不存在");
        }

        // 查询该执行下全部模型调用，按执行序号升序
        List<AgentExecutionModelCallEntity> modelCalls = modelCallMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionModelCallEntity>()
                        .eq(AgentExecutionModelCallEntity::getExecutionId, execution.getId())
                        .orderByAsc(AgentExecutionModelCallEntity::getSequenceNo));
        // 查询该执行下全部工具调用，按执行序号升序
        List<AgentExecutionToolCallEntity> toolCalls = toolCallMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionToolCallEntity>()
                        .eq(AgentExecutionToolCallEntity::getExecutionId, execution.getId())
                        .orderByAsc(AgentExecutionToolCallEntity::getSequenceNo));
        // 组装完整审计VO返回
        return toVO(execution, modelCalls, toolCalls);
    }

    /**
     * 分页查询工作台任务对应的工具调用，避免将全部调用明细一次性加载到内存和网络响应中。
     *
     * @param request 空间、任务和分页参数
     * @return 工具调用分页结果
     */
    public PageVO<AgentToolCallVO> getToolCalls(AgentToolCallPageQueryDTO request) {
        if (request == null || request.spaceId() == null || request.taskIds() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具调用分页查询参数不完整");
        }
        List<Long> taskIds = request.taskIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (taskIds.isEmpty() || taskIds.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务 ID 集合必须包含 1~100 个任务");
        }
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(request.pageNum());
        pageParam.setPageSize(request.pageSize());
        pageParam.validate();
        spaceAccessService.requirePermission(request.spaceId(), TASK_READ);

        List<AgentExecutionEntity> executions = executionMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .eq(AgentExecutionEntity::getSpaceId, request.spaceId())
                        .in(AgentExecutionEntity::getWorkbenchTaskId, taskIds));
        if (executions.isEmpty()) {
            return PageVO.of(List.<AgentToolCallVO>of(), 0, pageParam);
        }
        Map<Long, Long> taskIdsByExecutionId = executions.stream()
                .collect(Collectors.toMap(AgentExecutionEntity::getId,
                        AgentExecutionEntity::getWorkbenchTaskId));
        Page<AgentExecutionToolCallEntity> page = toolCallMapper.selectPage(
                new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new LambdaQueryWrapper<AgentExecutionToolCallEntity>()
                        .in(AgentExecutionToolCallEntity::getExecutionId, taskIdsByExecutionId.keySet())
                        .orderByDesc(AgentExecutionToolCallEntity::getStartedAt)
                        .orderByDesc(AgentExecutionToolCallEntity::getId));
        List<AgentToolCallVO> records = page.getRecords().stream()
                .map(call -> new AgentToolCallVO(taskIdsByExecutionId.get(call.getExecutionId()), toolCall(call)))
                .toList();
        return PageVO.of(records, page.getTotal(), pageParam);
    }

    /**
     * 任务同步链路专用：最小Token用量投影
     * 仅返回Token统计字段，不做权限校验，供内部回调使用，避免依赖用户权限审计接口
     *
     * @param taskId 工作台任务ID
     * @return Token用量VO；无执行记录返回null
     */
    public AgentExecutionTokenUsageVO getTokenUsageByWorkbenchTask(Long taskId) {
        AgentExecutionEntity execution = executionMapper.selectOne(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));
        if (execution == null) {
            return null;
        }
        // 将模型快照信息转为Map
        Map<String, Object> model = map(execution.getModelSnapshot());
        // 组装Token用量VO返回
        return new AgentExecutionTokenUsageVO(execution.getId(), longValue(model.get("id")),
                execution.getModelConfigVersion(), decimalValue(model.get("inputPricePerMillion")),
                decimalValue(model.get("outputPricePerMillion")), TOKEN_PRICING_CURRENCY,
                TOKEN_PRICING_SCHEMA_VERSION, execution.getCreatedAt(),
                execution.getInputTokens(), execution.getInputTokensEstimated(),
                execution.getCachedInputTokens(), execution.getCachedInputTokensEstimated(),
                execution.getOutputTokens(), execution.getOutputTokensEstimated());
    }

    /**
     * 批量查询工作台任务Token用量，用于版本历史展示
     * 批量投影，避免循环逐条调用执行服务；限制单次最大查询100个任务
     *
     * @param request 批量查询DTO，包含任务ID集合
     * @return 任务Token用量批量结果集合
     */
    public List<AgentExecutionTokenUsageBatchVO> getTokenUsagesByWorkbenchTasks(
            AgentExecutionTokenUsageBatchQueryDTO request) {
        // 处理入参，过滤null、去重
        List<Long> taskIds = request == null || request.taskIds() == null ? List.of()
                : request.taskIds().stream().filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) {
            return List.of();
        }

        // 限制批量查询上限，防止大SQL
        if (taskIds.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多查询 100 个任务的 Token 用量");
        }

        // 批量查询执行记录，映射为批量Token用量VO
        return executionMapper.selectList(new LambdaQueryWrapper<AgentExecutionEntity>()
                        .in(AgentExecutionEntity::getWorkbenchTaskId, taskIds)).stream()
                .map(execution -> new AgentExecutionTokenUsageBatchVO(
                        execution.getWorkbenchTaskId(), execution.getInputTokens(),
                        execution.getInputTokensEstimated(), execution.getCachedInputTokens(),
                        execution.getCachedInputTokensEstimated(), execution.getOutputTokens(),
                        execution.getOutputTokensEstimated()))
                .toList();
    }

    /**
     * 批量查询评估证据数据
     * <p>
     * 批量返回确定性 Evaluator 所需的执行终态和工具调用计数，不暴露调用参数或结果正文。
     * 用于 evaluation-service 做指标评估；仅返回统计元数据，不携带Prompt、工具入参/返回报文。
     * 限制：单次查询任务ID数量范围1~100。
     * </p>
     * @param request 查询DTO，携带待查询的任务ID列表
     * @return AgentEvaluationEvidenceVO 列表，每条对应一次Agent执行评估证据
     * @throws BusinessException 任务ID为空或数量超出100上限时抛出BAD_REQUEST
     */
    public List<AgentEvaluationEvidenceVO> getEvaluationEvidence(AgentEvaluationEvidenceQueryDTO request) {
        // 提取并清洗任务ID列表：过滤null、去重
        List<Long> taskIds = request == null || request.taskIds() == null ? List.of()
                : request.taskIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // 参数校验：任务ID必须1~100条
        if (taskIds.isEmpty() || taskIds.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务 ID 集合必须包含 1~100 项");
        }
        // 根据TaskId批量查询Agent执行主记录
        List<AgentExecutionEntity> executions = executionMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .in(AgentExecutionEntity::getWorkbenchTaskId, taskIds));
        // 没有执行记录直接返回空集合
        if (executions.isEmpty()) {
            return List.of();
        }
        // 提取所有executionId，用于批量查询工具调用明细
        List<Long> executionIds = executions.stream()
                .map(AgentExecutionEntity::getId)
                .toList();
        // 批量查询所有关联的【工具调用】记录
        List<AgentExecutionToolCallEntity> toolCalls = toolCallMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionToolCallEntity>()
                        .in(AgentExecutionToolCallEntity::getExecutionId, executionIds));
        // 按executionId聚合统计：总调用数、失败调用数、外部MCP调用数
        Map<Long, ToolCounts> counts = new HashMap<>();
        for (AgentExecutionToolCallEntity call : toolCalls) {
            ToolCounts current = counts.computeIfAbsent(call.getExecutionId(), ignored -> new ToolCounts());
            // 工具调用总数+1
            current.total++;
            if (AgentExecutionStatus.FAILED.name().equals(call.getStatus())) {
                // 失败计数+1
                current.failed++;
            }
            if (call.getMcpServerId() != null) {
                // 外部MCP调用计数+1
                current.externalMcp++;
            }
        }
        // 将执行主记录与聚合统计结果映射为评估证据VO返回
        return executions.stream().map(execution -> {
            ToolCounts value = counts.getOrDefault(execution.getId(), new ToolCounts());
            return new AgentEvaluationEvidenceVO(execution.getWorkbenchTaskId(), execution.getId(),
                    execution.getStatus(), execution.getTraceId(), execution.getSpanId(),
                    execution.getStartedAt(), execution.getFinishedAt(), value.total, value.failed,
                    value.externalMcp);
        }).toList();
    }

    /**
     * 统计指定空间时间范围内工具调用来源分布
     * 聚合统计各个工具来源的调用次数，返回总调用数+各来源明细
     *
     * @param query 查询条件DTO：空间ID、时间起止
     * @return 工具用量统计VO
     */
    public AgentToolUsageStatsVO getToolUsageStats(AgentToolUsageQueryDTO query) {
        // 参数合法性校验：空间、起止时间不能为空，开始时间必须早于结束时间
        if (query == null || query.spaceId() == null || query.startAt() == null || query.endAt() == null
                || !query.startAt().isBefore(query.endAt())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具用量查询条件不合法");
        }
        // 校验空间用量读取权限
        spaceAccessService.requirePermission(query.spaceId(), USAGE_READ);

        // 调用Mapper聚合查询工具来源统计，空调用次数补0
        List<AgentToolSourceCountVO> sources = toolCallMapper.aggregateBySource(query).stream()
                .map(row -> new AgentToolSourceCountVO(row.source(), row.calls() == null ? 0 : row.calls()))
                .toList();

        // 计算总工具调用次数，组装返回
        return new AgentToolUsageStatsVO(
                sources.stream().mapToLong(AgentToolSourceCountVO::calls).sum(), sources);
    }

    /**
     * 将Agent执行实体、模型调用、工具调用实体组装成完整审计VO
     * 内部会解析各类JSON快照字段，转换成VO子对象
     *
     * @param execution Agent执行主记录
     * @param modelCalls 模型调用列表
     * @param toolCalls 工具调用列表
     * @return 完整Agent执行审计VO
     */
    private AgentExecutionAuditVO toVO(AgentExecutionEntity execution,
                                       List<AgentExecutionModelCallEntity> modelCalls,
                                       List<AgentExecutionToolCallEntity> toolCalls) {
        return new AgentExecutionAuditVO(
                execution.getId(), execution.getWorkbenchTaskId(), execution.getSpaceId(),
                execution.getAgentId(), execution.getAgentNameSnapshot(), execution.getAgentConfigVersion(),
                execution.getMaxIterations(), execution.getExecutionTimeoutSeconds(), execution.getStatus(),
                execution.getCancelRequested(), execution.getPromptHash(), execution.getExecutionSnapshotHash(),
                execution.getExecutionSnapshotSchemaVersion(),
                modelSnapshot(execution), skillSnapshot(execution), toolDefinitions(execution),
                externalMcps(execution), modelCalls.stream().map(this::modelCall).toList(),
                toolCalls.stream().map(this::toolCall).toList(), execution.getInputTokens(),
                execution.getInputTokensEstimated(), execution.getCachedInputTokens(),
                execution.getCachedInputTokensEstimated(), execution.getOutputTokens(),
                execution.getOutputTokensEstimated(), execution.getStartedAt(), execution.getFinishedAt(),
                execution.getCreatedAt());
    }

    /**
     * 解析执行记录中的模型快照JSON，转为VO子对象
     * @param execution Agent执行实体
     * @return 模型快照VO
     */
    private AgentExecutionAuditVO.ModelSnapshot modelSnapshot(AgentExecutionEntity execution) {
        Map<String, Object> model = map(execution.getModelSnapshot());
        return new AgentExecutionAuditVO.ModelSnapshot(
                longValue(model.get("id")), stringValue(model.get("modelKey")),
                execution.getModelDisplayNameSnapshot(), execution.getModelConfigVersion());
    }

    /**
     * 解析Skill相关快照JSON，包含绑定Skill、路由快照、选中版本ID
     * @param execution Agent执行实体
     * @return Skill快照VO
     */
    private AgentExecutionAuditVO.SkillSnapshot skillSnapshot(AgentExecutionEntity execution) {
        Map<String, Object> router = map(execution.getSkillRouterSnapshotJson());
        // 解析绑定Skill列表
        List<AgentExecutionAuditVO.BoundSkill> bound = listMap(execution.getSkillSnapshotJson()).stream()
                .map(value -> new AgentExecutionAuditVO.BoundSkill(
                        longValue(value.get("skillId")), longValue(value.get("skillVersionId")),
                        integerValue(value.get("versionNo")), stringValue(value.get("name")),
                        stringValue(value.get("activationDescription")), stringValue(value.get("sha256"))))
                .toList();
        // 解析本次执行选中的Skill版本ID集合
        List<Long> selected = JsonUtils.parse(execution.getSelectedSkillVersionIdsJson(),
                new TypeReference<List<Long>>() { });
        return new AgentExecutionAuditVO.SkillSnapshot(
                execution.getSkillSelectionMode(), execution.getSkillSelectionEffectiveMode(),
                execution.getSkillInstructionHash(), execution.getSkillRouterModelId(),
                longValue(router.get("durationMs")), stringValue(router.get("fallbackReason")),
                stringValue(router.get("inputSha256")), stringValue(router.get("responseSha256")),
                bound, selected == null ? List.of() : selected);
    }

    /**
     * 解析工具定义快照JSON，转为工具定义快照VO列表
     * @param execution Agent执行实体
     * @return 工具定义快照列表
     */
    private List<AgentExecutionAuditVO.ToolDefinitionSnapshot> toolDefinitions(AgentExecutionEntity execution) {
        return listMap(execution.getToolDefinitionSnapshotJson()).stream()
                .map(value -> new AgentExecutionAuditVO.ToolDefinitionSnapshot(
                        stringValue(value.get("name")), stringValue(value.get("source")),
                        stringValue(value.get("sourceKey")), longValue(value.get("mcpServerId"))))
                .toList();
    }

    /**
     * 解析外部MCP服务快照JSON，转为MCP快照VO列表
     * @param execution Agent执行实体
     * @return 外部MCP快照列表
     */
    private List<AgentExecutionAuditVO.ExternalMcpSnapshot> externalMcps(AgentExecutionEntity execution) {
        return listMap(execution.getExternalMcpSnapshotJson()).stream()
                .map(value -> new AgentExecutionAuditVO.ExternalMcpSnapshot(
                        longValue(value.get("serverId")), stringValue(value.get("serverKey")),
                        longValue(value.get("configVersion")), stringValue(value.get("endpointSha256")),
                        stringValue(value.get("authType")), stringList(value.get("toolWhitelist"))))
                .toList();
    }

    /**
     * 模型调用实体转VO
     * @param call 模型调用数据库实体
     * @return 模型调用VO
     */
    private AgentExecutionAuditVO.ModelCall modelCall(AgentExecutionModelCallEntity call) {
        return new AgentExecutionAuditVO.ModelCall(
                call.getSequenceNo(), call.getModelId(), call.getModelConfigVersion(), call.getModelKey(),
                call.getMaxOutputTokens(), call.getTemperature(), call.getStreaming(), call.getMessagesSha256(),
                call.getMessagesSize(), call.getResponseSha256(), call.getResponseSize(), call.getStatus(),
                call.getErrorType(), call.getStartedAt(), call.getFinishedAt());
    }

    /**
     * 工具调用实体转VO
     * @param call 工具调用数据库实体
     * @return 工具调用VO
     */
    private AgentExecutionAuditVO.ToolCall toolCall(AgentExecutionToolCallEntity call) {
        return new AgentExecutionAuditVO.ToolCall(
                call.getSequenceNo(), call.getToolName(), call.getToolSource(), call.getToolSourceKey(),
                call.getMcpServerId(), call.getSkillVersionId(), call.getArgumentsSha256(), call.getArgumentsSize(),
                call.getResultSha256(), call.getResultSize(), call.getStatus(), call.getErrorType(),
                call.getStartedAt(), call.getFinishedAt());
    }

    /**
     * JSON字符串解析为Map；解析失败返回空Map
     * @param json json字符串
     * @return Map对象
     */
    private Map<String, Object> map(String json) {
        Map<String, Object> value = JsonUtils.parse(json, new TypeReference<Map<String, Object>>() { });
        return value == null ? Map.of() : value;
    }

    /**
     * JSON字符串解析为List<Map>；解析失败返回空List
     * @param json json字符串
     * @return List<Map>
     */
    private List<Map<String, Object>> listMap(String json) {
        List<Map<String, Object>> value = JsonUtils.parse(json,
                new TypeReference<List<Map<String, Object>>>() { });
        return value == null ? List.of() : value;
    }

    /**
     * 对象转字符串列表；非List类型返回空列表
     * @param value 原始对象
     * @return 字符串列表
     */
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /**
     * 对象安全转为Long；null/解析异常返回null
     * @param value 原始对象
     * @return Long值或null
     */
    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 对象安全转为Integer；null/解析异常返回null
     * @param value 原始对象
     * @return Integer值或null
     */
    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 对象安全转为String；null返回null
     * @param value 原始对象
     * @return 字符串或null
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 获取任务对应的回放最小身份投影
     * <p>
     * 要求任务下仅有【单条执行】记录；若存在多条或无执行记录，直接返回标记数量的无效身份，不自动挑选任意一条。
     * 校验快照版本、快照哈希完整性，同时识别是否存在外部MCP依赖，返回给上游做Replay准入判断。
     * </p>
     * @param taskId 工作台任务ID
     * @return AgentExecutionReplayIdentityVO 回放身份投影，包含快照有效性与外部MCP标记
     */
    public AgentExecutionReplayIdentityVO getReplayIdentity(Long taskId) {
        // 根据任务ID查询全部关联Agent执行记录
        List<AgentExecutionEntity> executions = executionMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));
        // 执行记录数量不等于1：返回无效身份，携带执行条数，上游直接拒绝回放
        if (executions.size() != 1) {
            return new AgentExecutionReplayIdentityVO(executions.size(), null, null, null, false, false);
        }
        // 仅有唯一一条执行记录，取出该执行实体
        AgentExecutionEntity execution = executions.getFirst();
        // 快照有效性校验：schema=V3、快照JSON、哈希存在，并且重新规范化哈希比对一致
        boolean valid = execution.getExecutionSnapshotSchemaVersion() != null
                && execution.getExecutionSnapshotSchemaVersion() == 3
                && execution.getExecutionSnapshotJson() != null
                && execution.getExecutionSnapshotHash() != null
                && execution.getExecutionSnapshotHash().equals(
                SnapshotCanonicalV3Utils.hashEnvelope(execution.getExecutionSnapshotJson()));
        // 解析外部MCP快照节点
        JsonNode externalMcp = JsonUtils.parse(execution.getExternalMcpSnapshotJson(), JsonNode.class);
        // 判断快照是否包含外部MCP（非空数组）
        boolean externalMcpPresent = externalMcp != null && (!externalMcp.isArray() || !externalMcp.isEmpty());
        // 组装并返回回放身份VO
        return new AgentExecutionReplayIdentityVO(1, execution.getId(),
                execution.getExecutionSnapshotSchemaVersion(), execution.getExecutionSnapshotHash(), valid,
                externalMcpPresent);
    }

    /**
     * 工具调用计数临时内部类，用于聚合统计单次执行的工具调用指标
     * <p>仅在getEvaluationEvidence内使用，存储总调用数、失败调用数、外部MCP调用数量。</p>
     */
    private static final class ToolCounts {
        /** 工具调用总次数 */
        private long total;
        /** 工具调用失败次数 */
        private long failed;
        /** 外部MCP服务调用次数 */
        private long externalMcp;
    }
}
