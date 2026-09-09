package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentTaskOptionQueryDTO;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentTaskOptionVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.SpaceBudgetVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.convertor.TaskConvertor;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.dto.TaskFocusRegionDTO;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.param.TaskActivitySearchParam;
import com.agentdoc.task.pojo.param.TaskCreateOptionsParam;
import com.agentdoc.task.pojo.param.TaskSearchParam;
import com.agentdoc.task.pojo.vo.TaskActivityVO;
import com.agentdoc.task.pojo.vo.TaskCreateOptionsVO;
import com.agentdoc.task.pojo.vo.TaskDocumentContextVO;
import com.agentdoc.task.pojo.vo.TaskListItemVO;
import com.agentdoc.task.pojo.vo.TaskFocusRegionVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.pojo.vo.TaskStatsVO;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_CREATE;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_TERMINATE;
import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_EXPORT;

/**
 * 任务业务服务
 * <p>核心任务业务能力：任务创建、查询、导出、重跑、终止、重试、审批退回重改；
 * 包含权限校验、Token预算计算、任务能力令牌签发、关注区域校验、任务执行上下文构建、能力令牌校验；
 * 负责任务状态流转、MQ消息投递、审计日志记录。</p>
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TokenUsageDetailMapper tokenUsageDetailMapper;
    private final A2aTaskClient a2aTaskClient;
    private final AgentFeign agentFeign;
    private final DocumentFeign documentFeign;
    private final TaskMessagePublisher messagePublisher;
    private final TaskCapabilityCryptoService cryptoService;
    private final AuthFeign authFeign;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final TaskCapabilityVerifier taskCapabilityVerifier;

    /**
     * 创建Agent任务
     * <p>
     * 执行步骤：
     * <ol>
     * <li>获取当前操作用户ID；</li>
     * <li>Feign调用文档服务获取文档执行上下文；校验Agent存在并且已启用；校验Agent与文档属于同一空间；校验文档在Agent授权文档范围内；</li>
     * <li>Token预算计算：任务、Agent 和空间预算均作为上限，取全部非空值的最小值；</li>
     * <li>新建任务实体，状态置为{@link TaskStatus#PENDING}，插入数据库；</li>
     * <li>调用auth‑service申请任务短时Agent能力令牌，指定可执行动作；令牌加密存入任务记录；投递MQ消息触发任务异步执行；记录审计日志；</li>
     * <li>令牌申请/MQ投递发生异常：任务更新为FAILED，填写错误信息，结束时间；向外抛出内部异常。</li>
     * </ol>
     * </p>
     * @param dto 任务创建入参
     * @return 任务VO
     */
    public TaskVO create(TaskCreateDTO dto) {
        // 获取当前操作用户ID
        Long userId = AuthUtils.getUserIdOrException();
        // 根据文档Id查询文档相关信息（所属空间Id、文档类型、状态、版本等等）
        DocumentExecutionContextVO document = requireData(documentFeign.getExecutionContext(dto.documentId()));
        // 校验任务入参空间与文档所属空间必须一致
        if (!dto.spaceId().equals(document.spaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前空间与目标文档不一致");
        }
        // 归档文档禁止创建任务
        if (!document.normal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档文档不能创建任务");
        }
        DocType documentType = requireDocumentType(document.docType());
        // 根据agentId远程调用查询Agent信息
        AgentExecutionProfileVO agent = requireData(agentFeign.getExecutionProfile(dto.agentId()));

        // 校验Agent是否启用、文档是否匹配
        if (!agent.enabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已禁用");
        }
        // Agent与文档必须属于同一个空间
        if (!dto.spaceId().equals(agent.spaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 与文档不属于同一空间");
        }

        // 校验Agent文档访问范围配置，判断目标documentId是否在允许列表内
        requireDocumentScope(agent, document.documentId());
        // 查询空间 Agent 执行预算
        SpaceBudgetVO spaceBudget = requireData(documentFeign.getSpaceExecutionBudget(document.spaceId()));
        // 计算有效token预算：取任务、Agent、空间三者非空预算的最小值
        Long budget = effectiveBudget(dto.tokenBudget(), agent.tokenBudget(), spaceBudget.tokenBudget());
        if (budget != null && budget < TaskConstant.MIN_TOKEN_BUDGET) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务 Token 预算必须大于 0");
        }
        // 解析、校验、规范化关注区域
        FocusRegions focusRegions = resolveFocusRegions(dto.readScope(), dto.focusRegions(), document.contentLength());

        // 任务落库，构建任务实体
        TaskEntity entity = dto.toEntity(document.spaceId(), documentType.getCode(), budget, agent.configVersion(),
                focusRegions.scope(), focusRegions.json(), userId);
        entity.setId(IdWorker.getId());
        entity.setTaskNo(buildTaskNo(entity.getId()));
        taskMapper.insert(entity);

        // 生成任务能力令牌，并更新任务记录
        try {
            // 令牌加密存储，不在数据库留存明文
            entity.setCapabilityToken(issueEncryptedCapability(entity));
            // 更新任务
            taskMapper.updateById(entity);
            // 投递MQ，触发异步任务消费执行
            messagePublisher.publish(entity.getId());
            // 记录创建审计日志
            auditLogService.recordHuman(entity.getSpaceId(), AuditAction.TASK_CREATED,
                    AuditTargetType.TASK, entity.getId(), null);
        } catch (RuntimeException e) {
            // 申请令牌或发消息失败，任务置失败状态，记录错误信息
            entity.setStatus(TaskStatus.FAILED.getCode());
            entity.setErrorMessage("任务能力令牌签发或消息发布失败：" + e.getMessage());
            entity.setEndTime(LocalDateTime.now());
            taskMapper.updateById(entity);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "任务能力令牌签发或消息发布失败");
        }
        return TaskVO.from(entity);
    }

    /**
     * 查询任务创建页在选定文档后的可用 Agent 与预算信息。
     *
     * @param param 当前空间和目标文档
     * @return 文档上下文、空间预算和可执行 Agent
     */
    public TaskCreateOptionsVO getCreateOptions(TaskCreateOptionsParam param) {
        DocumentExecutionContextVO document = requireData(documentFeign.getExecutionContext(param.documentId()));
        if (!param.spaceId().equals(document.spaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前空间与目标文档不一致");
        }
        if (!document.normal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档文档不能创建任务");
        }
        // 获取空间执行预算
        SpaceBudgetVO spaceBudget = requireData(documentFeign.getSpaceExecutionBudget(param.spaceId()));
        // 查询当前空间下，可访问该文档的Agent列表
        List<AgentTaskOptionVO> agents = requireData(agentFeign.queryTaskOptions(
                new AgentTaskOptionQueryDTO(param.spaceId(), param.documentId())));
        return new TaskCreateOptionsVO(param.spaceId(), param.documentId(),
                requireDocumentType(document.docType()), document.version(),
                document.contentLength(), spaceBudget.tokenBudget(), agents);
    }

    /**
     * 分页查询空间下任务列表
     * @param spaceId 空间ID
     * @param pageParam 分页参数
     * @return 分页任务VO
     */
    public PageVO<TaskVO> list(Long spaceId, PageParam pageParam) {
        // Feign校验当前用户在该空间具备【读取】权限
        requirePermission(spaceId, TASK_READ);
        // 分页参数合法性校验
        pageParam.validate();
        Page<TaskEntity> page = taskMapper.selectPage(new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getSpaceId, spaceId)
                        .orderByDesc(TaskEntity::getCreatedAt));
        return PageVO.of(page.getRecords().stream().map(TaskVO::from).toList(), page.getTotal(), pageParam);
    }

    /**
     * 按页面筛选条件查询任务，并批量回填关联资源的展示名称。
     *
     * @param param 任务筛选和分页参数
     * @return 任务列表摘要
     */
    public PageVO<TaskListItemVO> search(TaskSearchParam param) {
        requirePermission(param.getSpaceId(), TASK_READ);
        validateSearchParam(param);
        // 构建查询条件wrapper
        LambdaQueryWrapper<TaskEntity> wrapper = buildSearchWrapper(param);

        Page<TaskEntity> page = taskMapper.selectPage(
                new Page<>(param.getPageNum(), param.getPageSize()), wrapper);
        List<TaskEntity> tasks = page.getRecords();
        if (tasks.isEmpty()) {
            return PageVO.of(List.of(), page.getTotal(), param);
        }
        // 批量拉取Agent、文档、用户信息，组装列表VO
        return PageVO.of(toListItems(tasks), page.getTotal(), param);
    }

    /**
     * 导出空间执行记录。导出是独立的高权限操作，不能通过任务分页查询绕过权限。
     */
    public byte[] export(TaskSearchParam param) {
        requirePermission(param.getSpaceId(), USAGE_EXPORT);
        validateSearchParam(param);
        // 根据筛选条件查询全部任务
        List<TaskEntity> tasks = taskMapper.selectList(buildSearchWrapper(param));
        StringBuilder csv = new StringBuilder("\uFEFF");
        // 写入csv表头
        appendCsvRow(csv, "任务编号", "任务名称", "Agent", "Token", "状态", "开始时间", "结束时间");
        // 填充每行数据
        for (TaskListItemVO task : toListItems(tasks)) {
            appendCsvRow(csv, task.taskNo(), task.name(),
                    task.agentName() == null ? task.agentId() : task.agentName(), task.tokensUsed(),
                    task.status() == null ? null : task.status().getName(), task.startTime(), task.endTime());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验搜索参数合法性
     * @param param 任务搜索参数
     */
    private void validateSearchParam(TaskSearchParam param) {
        param.validate();
        // 校验时间区间：开始时间必须早于结束时间
        if (param.getStartedFrom() != null && param.getStartedTo() != null
                && !param.getStartedFrom().isBefore(param.getStartedTo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务执行时间范围不合法");
        }
    }

    /**
     * 根据搜索参数构建MyBatis‑Plus查询Wrapper
     * @param param 搜索参数
     * @return 查询条件包装器
     */
    private LambdaQueryWrapper<TaskEntity> buildSearchWrapper(TaskSearchParam param) {
        LambdaQueryWrapper<TaskEntity> wrapper = new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getSpaceId, param.getSpaceId())
                .orderByDesc(TaskEntity::getCreatedAt)
                .orderByDesc(TaskEntity::getId);
        if (param.getStatus() != null) {
            wrapper.eq(TaskEntity::getStatus, param.getStatus().getCode());
        }
        if (param.getAgentId() != null) {
            wrapper.eq(TaskEntity::getAgentId, param.getAgentId());
        }
        // 按模型筛选：通过tokenUsageDetailMapper查询关联taskId再in查询
        if (param.getModelId() != null) {
            List<Long> taskIds = tokenUsageDetailMapper.listTaskIdsByModelAndDate(
                    param.getSpaceId(), param.getModelId(), param.getStartedFrom(), param.getStartedTo());
            wrapper.in(TaskEntity::getId, taskIds.isEmpty() ? List.of(-1L) : taskIds);
        }
        if (param.getDocumentId() != null) {
            wrapper.eq(TaskEntity::getDocumentId, param.getDocumentId());
        }
        if (param.getStartedFrom() != null) {
            wrapper.ge(TaskEntity::getStartTime, param.getStartedFrom());
        }
        if (param.getStartedTo() != null) {
            wrapper.lt(TaskEntity::getStartTime, param.getStartedTo());
        }
        // 关键词模糊搜索：任务名称、指令、任务编号
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            wrapper.and(query -> query.like(TaskEntity::getName, keyword)
                    .or().like(TaskEntity::getInstruction, keyword)
                    .or().like(TaskEntity::getTaskNo, keyword));
        }
        return wrapper;
    }

    /**
     * 批量查询关联资源，将TaskEntity转为TaskListItemVO
     * @param tasks 任务实体列表
     * @return 任务列表VO
     */
    private List<TaskListItemVO> toListItems(List<TaskEntity> tasks) {
        // 批量拉取Agent信息
        Map<Long, AgentRefVO> agents = fetchAgents(tasks.stream()
                .map(TaskEntity::getAgentId).filter(Objects::nonNull).distinct().toList());
        // 批量拉取文档信息
        Map<Long, DocumentRefVO> documents = fetchDocuments(tasks.stream()
                .map(TaskEntity::getDocumentId).filter(Objects::nonNull).distinct().toList());
        // 批量拉取用户信息
        Map<Long, UserRefVO> users = fetchUsers(tasks.stream()
                .map(TaskEntity::getCreatedBy).filter(Objects::nonNull).distinct().toList());
        // 组装VO
        return tasks.stream()
                .map(task -> TaskConvertor.toListItemVO(task, agents.get(task.getAgentId()),
                        documents.get(task.getDocumentId()), users.get(task.getCreatedBy())))
                .toList();
    }

    /**
     * 追加CSV一行，处理引号转义
     * @param csv StringBuilder对象
     * @param values 单元格数据
     */
    private void appendCsvRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            String value = values[i] == null ? "" : String.valueOf(values[i]);
            // 双引号转义：csv内部双引号替换为两个双引号
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    /**
     * 批量查询Agent引用信息
     * @param agentIds AgentId集合
     * @return agentId -> AgentRefVO
     */
    private Map<Long, AgentRefVO> fetchAgents(List<Long> agentIds) {
        if (agentIds.isEmpty()) {
            return Map.of();
        }
        return requireData(agentFeign.queryAgentRefs(new AgentBatchQueryDTO(agentIds))).stream()
                .collect(Collectors.toMap(AgentRefVO::id, Function.identity()));
    }

    /**
     * 批量查询文档引用信息
     * @param documentIds 文档ID集合
     * @return documentId -> DocumentRefVO
     */
    private Map<Long, DocumentRefVO> fetchDocuments(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return requireData(documentFeign.getDocumentRefs(documentIds)).stream()
                .collect(Collectors.toMap(DocumentRefVO::id, Function.identity()));
    }

    /**
     * 查询空间最近任务执行动态，按最近心跳、结束、开始和创建时间倒序。
     *
     * @param param 查询参数（空间和分页）
     * @return 执行动态分页结果
     */
    public PageVO<TaskActivityVO> listActivity(TaskActivitySearchParam param) {
        requirePermission(param.spaceId(), TASK_READ);
        PageParam pageParam = param.pageParam() == null ? new PageParam() : param.pageParam();
        pageParam.validate();
        // 排序优先级：lastHeartbeatAt > endTime > startTime > createdAt
        Page<TaskEntity> page = taskMapper.selectPage(
                new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getSpaceId, param.spaceId())
                        .orderByDesc(TaskEntity::getLastHeartbeatAt)
                        .orderByDesc(TaskEntity::getEndTime)
                        .orderByDesc(TaskEntity::getStartTime)
                        .orderByDesc(TaskEntity::getCreatedAt));
        // 批量拉取创建人信息
        Map<Long, UserRefVO> users = fetchUsers(page.getRecords().stream()
                .map(TaskEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<TaskActivityVO> records = page.getRecords().stream()
                .map(task -> new TaskActivityVO(
                        task.getId(),
                        task.getName(),
                        task.getAgentId(),
                        TaskStatus.fromCode(task.getStatus()),
                        displayName(users.get(task.getCreatedBy())),
                        activityTime(task)))
                .toList();
        return PageVO.of(records, page.getTotal(), pageParam);
    }

    /**
     * 批量查询用户引用信息
     * @param userIds 用户ID集合
     * @return userId -> UserRefVO
     */
    private Map<Long, UserRefVO> fetchUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRefVO> users = authFeign.queryUsers(new UserBatchQueryDTO(userIds)).data();
        if (users == null) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(UserRefVO::id, user -> user));
    }

    /**
     * 获取用户展示名称，优先昵称，无昵称使用用户名
     * @param user 用户对象
     * @return 展示名称
     */
    private String displayName(UserRefVO user) {
        if (user == null) {
            return null;
        }
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

    /**
     * 获取任务动态展示时间，优先级：心跳时间 > 结束时间 > 开始时间 > 创建时间
     * @param task 任务实体
     * @return 展示时间
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
     * 查询空间任务总数与截至昨日的任务数。
     *
     * @param spaceId 空间 ID
     * @return 两个原始数量，差值由前端计算
     */
    public TaskStatsVO getStats(Long spaceId) {
        requirePermission(spaceId, TASK_READ);
        // 昨日零点
        LocalDateTime yesterdayStart = LocalDate.now().atStartOfDay();
        long totalCount = taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getSpaceId, spaceId));
        long countAsOfYesterday = taskMapper.selectCount(new LambdaQueryWrapper<TaskEntity>()
                .eq(TaskEntity::getSpaceId, spaceId)
                .lt(TaskEntity::getCreatedAt, yesterdayStart));
        return new TaskStatsVO(totalCount, countAsOfYesterday);
    }

    /**
     * 获取任务详情
     * @param id 任务ID
     * @return 任务VO
     */
    public TaskVO detail(Long id) {
        // 校验任务是否存在
        TaskEntity entity = require(id);
        // Feign校验当前用户在该空间具备【读取】权限
        requirePermission(entity.getSpaceId(), TASK_READ);
        return TaskVO.from(entity);
    }

    /**
     * 手动触发待运行任务，将任务消息重新投递到执行队列。
     *
     * @param id 任务 ID
     * @return 当前任务信息
     */
    public TaskVO run(Long id) {
        TaskEntity entity = require(id);
        requirePermission(entity.getSpaceId(), TASK_CREATE);
        // 仅PENDING待运行状态允许手动触发
        if (TaskStatus.fromCode(entity.getStatus()) != TaskStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有待运行的任务可以手动触发");
        }
        // 必须存在能力令牌才能执行
        if (entity.getCapabilityToken() == null || entity.getCapabilityToken().isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务缺少执行能力令牌，请重新创建任务");
        }
        try {
            // 重新投递MQ消息
            messagePublisher.publish(entity.getId());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "任务执行消息发布失败");
        }
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.TASK_RETRY,
                AuditTargetType.TASK, entity.getId(), "手动重新投递待运行任务");
        return TaskVO.from(require(id));
    }

    /**
     * 终止任务
     * <p>所有可取消的非终态均允许终止；远端任务先进入 CANCELING，再调用 A2A Cancel。</p>
     * @param id 任务ID
     * @return 终止后任务VO
     */
    public TaskVO terminate(Long id) {
        // 校验任务是否存在
        TaskEntity entity = require(id);
        // Feign校验当前用户在该空间具备【编辑】权限
        requirePermission(entity.getSpaceId(), TASK_TERMINATE);
        // 任务状态转换
        TaskStatus current = TaskStatus.fromCode(entity.getStatus());
        // 若当前任务状态非【待运行】、【运行中】，则禁止【终止】
        if (!current.canTerminate()) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前任务状态不允许终止");
        }
        // 若当前任务非【待运行】且A2A任务Id不为空
        if (current != TaskStatus.PENDING && entity.getA2aTaskId() != null) {
            // 远端任务先进入 CANCELING，再调用 A2A Cancel
            int canceling = taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                    .eq(TaskEntity::getId, id)
                    .eq(TaskEntity::getStatus, current.getCode())
                    .set(TaskEntity::getStatus, TaskStatus.CANCELING.getCode()));
            if (canceling == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "任务状态已发生变化，请刷新后重试");
            }
            // 异步调用A2A取消任务
            a2aTaskClient.cancel(entity.getA2aTaskId(), cryptoService.decrypt(entity.getCapabilityToken()));
            return TaskVO.from(require(id));
        }
        // 乐观锁更新：仅当状态为待执行/运行中才更新，受并发状态变更保护
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, id)
                .eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                .set(TaskEntity::getStatus, TaskStatus.TERMINATED.getCode())
                .set(TaskEntity::getErrorMessage, "用户主动终止")
                .set(TaskEntity::getEndTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务状态已发生变化，请刷新后重试");
        }
        // 日志记录
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.TASK_TERMINATED,
                AuditTargetType.TASK, entity.getId(), "用户主动终止");
        return TaskVO.from(require(id));
    }

    /**
     * 重新运行异常任务，复用原任务输入并创建新的任务记录。
     * <p>Agent 执行以任务 ID 做幂等键，因此不能重置原任务，否则会回放原失败执行。</p>
     *
     * @param id 原任务 ID
     * @return 新建的待运行任务
     */
    public TaskVO rerun(Long id) {
        TaskEntity entity = require(id);
        requirePermission(entity.getSpaceId(), TASK_CREATE);
        // 只有FAILED失败状态允许重跑
        if (TaskStatus.fromCode(entity.getStatus()) != TaskStatus.FAILED) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有异常失败的任务可以重跑");
        }

        // 校验文档有效性
        DocumentExecutionContextVO document = requireData(documentFeign.getExecutionContext(entity.getDocumentId()));
        if (!entity.getSpaceId().equals(document.spaceId()) || !document.normal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "目标文档当前不可用于任务重跑");
        }
        // 校验Agent可用
        AgentExecutionProfileVO agent = requireData(agentFeign.getExecutionProfile(entity.getAgentId()));
        if (!agent.enabled() || !entity.getSpaceId().equals(agent.spaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务 Agent 当前不可用");
        }
        requireDocumentScope(agent, entity.getDocumentId());

        // 复制生成新任务实体，parentTaskId指向原任务
        TaskEntity rerunTask = copyForRerun(entity, agent.configVersion(), AuthUtils.getUserIdOrException());
        taskMapper.insert(rerunTask);
        try {
            // 签发加密能力令牌
            rerunTask.setCapabilityToken(issueEncryptedCapability(rerunTask));
            taskMapper.updateById(rerunTask);
            messagePublisher.publish(rerunTask.getId());
        } catch (RuntimeException exception) {
            // 异常置任务失败
            rerunTask.setStatus(TaskStatus.FAILED.getCode());
            rerunTask.setErrorMessage("任务重跑能力令牌签发或消息发布失败：" + exception.getMessage());
            rerunTask.setEndTime(LocalDateTime.now());
            taskMapper.updateById(rerunTask);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "任务重跑消息发布失败");
        }
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.TASK_RETRY,
                AuditTargetType.TASK, rerunTask.getId(), "基于任务 " + entity.getTaskNo() + " 重新运行");
        return TaskVO.from(rerunTask);
    }

    /**
     * 为被退回的 Agent 变更创建后续任务。调用方负责校验变更审批权限。
     */
    public TaskVO createReviewRework(Long sourceTaskId, Long changeRequestId, String reviewComment) {
        TaskEntity source = require(sourceTaskId);
        // 校验文档可用
        DocumentExecutionContextVO document = requireData(documentFeign.getExecutionContext(source.getDocumentId()));
        if (!source.getSpaceId().equals(document.spaceId()) || !document.normal()) {
            throw new BusinessException(ErrorCode.CONFLICT, "目标文档当前不可用于退回重改");
        }
        // 校验Agent可用
        AgentExecutionProfileVO agent = requireData(agentFeign.getExecutionProfile(source.getAgentId()));
        if (!agent.enabled() || !source.getSpaceId().equals(agent.spaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "原任务 Agent 当前不可用");
        }
        requireDocumentScope(agent, source.getDocumentId());

        Long userId = AuthUtils.getUserIdOrException();
        // 复制任务基础数据
        TaskEntity rework = copyForRerun(source, agent.configVersion(), userId);
        // 任务名称追加后缀，做长度截断
        String suffix = "（审批重改）";
        String baseName = source.getName() == null ? "变更重改" : source.getName();
        rework.setName(baseName.length() + suffix.length() <= TaskConstant.MAX_TASK_NAME_LENGTH
                ? baseName + suffix
                : baseName.substring(0, TaskConstant.MAX_TASK_NAME_LENGTH - suffix.length()) + suffix);
        // 指令追加审批退回意见，做长度截断
        String instruction = source.getInstruction() + "\n\n审批退回意见：\n" + reviewComment
                + "\n\n请基于当前正式文档重新处理，并提交新的变更请求。原变更请求 ID：" + changeRequestId;
        rework.setInstruction(instruction.length() <= TaskConstant.MAX_TASK_INSTRUCTION_LENGTH
                ? instruction : instruction.substring(0, TaskConstant.MAX_TASK_INSTRUCTION_LENGTH));
        taskMapper.insert(rework);
        try {
            rework.setCapabilityToken(issueEncryptedCapability(rework));
            taskMapper.updateById(rework);
            // 事务提交完成后再投递消息，防止消费者读到未提交的任务记录
            publishTaskMessageAfterCommit(rework);
        } catch (RuntimeException exception) {
            rework.setStatus(TaskStatus.FAILED.getCode());
            rework.setErrorMessage("审批退回重改任务发布失败：" + exception.getMessage());
            rework.setEndTime(LocalDateTime.now());
            taskMapper.updateById(rework);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审批退回重改任务发布失败");
        }
        auditLogService.recordHuman(source.getSpaceId(), AuditAction.TASK_RETRY,
                AuditTargetType.TASK, rework.getId(), "由变更请求 " + changeRequestId + " 退回重改");
        return TaskVO.from(rework);
    }

    /**
     * 事务提交后投递任务消息，避免消费者在任务记录提交前读取不到新任务。
     */
    private void publishTaskMessageAfterCommit(TaskEntity task) {
        // 如果没有开启事务，直接发布
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishTaskMessage(task);
            return;
        }
        // 注册事务同步，事务提交完成后才执行消息投递
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishTaskMessage(task);
            }
        });
    }

    /**
     * 投递任务MQ消息，投递失败则更新任务为失败状态
     * @param task 任务实体
     */
    private void publishTaskMessage(TaskEntity task) {
        try {
            messagePublisher.publish(task.getId());
        } catch (RuntimeException exception) {
            // 消息发送失败，直接把任务置为FAILED
            taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                    .eq(TaskEntity::getId, task.getId())
                    .eq(TaskEntity::getStatus, TaskStatus.PENDING.getCode())
                    .set(TaskEntity::getStatus, TaskStatus.FAILED.getCode())
                    .set(TaskEntity::getErrorMessage, "审批退回重改任务发布失败：" + exception.getMessage())
                    .set(TaskEntity::getEndTime, LocalDateTime.now()));
        }
    }

    /**
     * 根据源任务复制生成重跑任务实体
     * @param source 源任务
     * @param agentConfigVersion agent配置版本
     * @param userId 创建人ID
     * @return 新任务实体
     */
    private TaskEntity copyForRerun(TaskEntity source, Long agentConfigVersion, Long userId) {
        TaskEntity target = new TaskEntity();
        target.setId(IdWorker.getId());
        target.setTaskNo(buildTaskNo(target.getId()));
        target.setSpaceId(source.getSpaceId());
        target.setAgentId(source.getAgentId());
        target.setAgentConfigVersion(agentConfigVersion);
        target.setDocumentId(source.getDocumentId());
        target.setDocumentType(source.getDocumentType());
        target.setName(source.getName());
        target.setInstruction(source.getInstruction());
        target.setStatus(TaskStatus.PENDING.getCode());
        target.setTokenBudget(source.getTokenBudget());
        target.setReadScope(source.getReadScope());
        target.setFocusRegionsJson(source.getFocusRegionsJson());
        target.setTokensEstimated(Boolean.FALSE);
        target.setParentTaskId(source.getId());
        target.setRetryCount(0);
        target.setCreatedBy(userId);
        return target;
    }

    /**
     * 调用auth服务签发任务能力令牌，返回加密后的令牌字符串
     * @param task 任务实体
     * @return 加密后的capabilityToken
     */
    private String issueEncryptedCapability(TaskEntity task) {
        DocType documentType = requireDocumentType(task.getDocumentType());
        // 根据文档类型区分允许执行的动作
        List<String> actions = documentType == DocType.DRAFT
                ? List.of(JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_WRITE_DRAFT)
                : List.of(JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_CREATE_CHANGE_REQUEST);
        String capability = requireData(authFeign.issueTaskCapability(
                new TaskCapabilityIssueDTO(task.getId(), task.getAgentId(), task.getSpaceId(),
                        task.getDocumentId(), actions)));
        return cryptoService.encrypt(capability);
    }

    /**
     * 组装 Agent 可见的任务文档上下文。
     */
    public TaskDocumentContextVO getTaskDocumentContext(Long taskId, DocumentExecutionContextVO document) {
        TaskEntity task = require(taskId);
        // 校验任务与传入文档上下文匹配
        if (!task.getDocumentId().equals(document.documentId()) || !task.getSpaceId().equals(document.spaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务文档上下文不匹配");
        }
        TaskReadScope scope = readScope(task);
        return new TaskDocumentContextVO(task.getId(), task.getTaskNo(), task.getDocumentId(), task.getSpaceId(),
                DocType.fromCode(task.getDocumentType()), document.version(), document.contentLength(), scope,
                focusRegions(task).stream().map(TaskFocusRegionVO::from).toList());
    }

    /**
     * 校验一次文档片段读取没有越过任务创建时固化的读取区间。
     * @param taskId 任务ID
     * @param start 片段起始偏移
     * @param length 片段长度
     */
    public void requireReadableRange(Long taskId, long start, int length) {
        TaskEntity task = require(taskId);
        // FULL模式不受限制
        if (readScope(task) == TaskReadScope.FULL) {
            return;
        }
        long end;
        try {
            end = Math.addExact(start, length);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档片段范围无效");
        }
        // 判断读取区间是否落在任意一个授权关注区域内
        boolean allowed = focusRegions(task).stream().anyMatch(region -> {
            long regionEnd = region.start() + region.length();
            return start >= region.start() && end <= regionEnd;
        });
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "读取范围超出任务授权区间");
        }
    }

    /**
     * 校验任务能力令牌：JWT密码学校验 + 业务维度双重校验
     * <p>
     * 校验项：令牌非空、JWT签名与基础 claim、JWT 内 taskId 匹配；数据库任务必须处于允许访问能力的活动状态；
     * JWT携带的agentId/spaceId/documentId与数据库任务实体完全匹配。
     * </p>
     * <p>解决JWT自包含的短板：JWT未过期，但任务已经停止/变更资源范围时拒绝访问。</p>
     * @param taskId 待校验任务ID
     * @param token X‑TASK‑CAPABILITY 任务能力令牌
     * @throws BusinessException 任意校验不通过抛出FORBIDDEN
     */
    public void checkCapability(Long taskId, String token) {
        // token不允许为空
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少任务能力令牌");
        }
        // 第一步：JWT密码学校验：签名、时间、agent基础业务claim
        var claims = taskCapabilityVerifier.verify(token);
        // JWT内taskId必须和入参taskId一致
        if (!String.valueOf(taskId).equals(claims.getClaimAsString(JwtConstant.CLAIM_TASK_ID))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务能力范围不匹配");
        }
        // 获取任务信息
        TaskEntity task = require(taskId);
        // 校验当前任务状态设置不允许访问文档(已分发、运行中、等待输入、等待授权)
        if (!TaskStatus.fromCode(task.getStatus()).allowsCapabilityAccess()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务当前不允许访问文档");
        }
        // 校验agentId、spaceId、documentId，和数据库任务记录完全匹配
        // 防止令牌被挪去访问其他agent、其他空间、其他文档
        if (!String.valueOf(task.getAgentId()).equals(claims.getClaimAsString(JwtConstant.CLAIM_AGENT_ID))
                || !String.valueOf(task.getSpaceId()).equals(claims.getClaimAsString(JwtConstant.CLAIM_SPACE_ID))
                || !String.valueOf(task.getDocumentId()).equals(
                claims.getClaimAsString(JwtConstant.CLAIM_DOCUMENT_ID))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务能力范围不匹配");
        }
    }

    /**
     * 获取任务实体，不存在抛出NOT_FOUND业务异常
     * @param id 任务ID
     * @return TaskEntity
     */
    public TaskEntity require(Long id) {
        TaskEntity entity = taskMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return entity;
    }

    /**
     * 计算有效token预算：取任务、Agent、空间三者非空预算的最小值
     * @param taskBudget 任务预算
     * @param agentBudget Agent预算
     * @param spaceBudget 空间预算
     * @return 最终生效预算，全部为空返回null
     */
    private Long effectiveBudget(Long taskBudget, Long agentBudget, Long spaceBudget) {
        List<Long> limits = new ArrayList<>();
        if (taskBudget != null) {
            limits.add(taskBudget);
        }
        if (agentBudget != null) {
            limits.add(agentBudget);
        }
        if (spaceBudget != null) {
            limits.add(spaceBudget);
        }
        return limits.stream().min(Long::compareTo).orElse(null);
    }

    /**
     * 解析、校验、规范化关注区域
     * @param requestedScope 请求的读取范围
     * @param requestedRegions 请求关注区域列表
     * @param documentLength 文档总长度
     * @return 封装scope与序列化后的json字符串
     */
    private FocusRegions resolveFocusRegions(TaskReadScope requestedScope, List<TaskFocusRegionDTO> requestedRegions,
                                             Long documentLength) {
        TaskReadScope scope = requestedScope == null ? TaskReadScope.FULL : requestedScope;
        List<TaskFocusRegionDTO> regions = requestedRegions == null ? List.of() : requestedRegions;
        // RANGES模式必须至少有一个关注区域
        if (scope == TaskReadScope.RANGES && regions.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "多区域读取必须至少选择一个关注区域");
        }
        long total = documentLength == null ? 0L : documentLength;
        // 校验每个关注区域不能越界，并且按start排序
        List<TaskFocusRegionDTO> normalized = regions.stream().map(region -> {
            long end;
            try {
                end = Math.addExact(region.start(), region.length());
            } catch (ArithmeticException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关注区域超出有效范围");
            }
            if (region.start() >= total || end > total) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关注区域超出文档长度");
            }
            return new TaskFocusRegionDTO(region.start(), region.length(), trimToNull(region.textPreview()),
                    trimToNull(region.instruction()));
        }).sorted((left, right) -> Long.compare(left.start(), right.start())).toList();
        return new FocusRegions(scope, normalized.isEmpty() ? null : JsonUtils.toJson(normalized));
    }

    /**
     * 反序列化任务中存储的关注区域JSON
     * @param task 任务实体
     * @return 关注区域列表
     */
    private List<TaskFocusRegionDTO> focusRegions(TaskEntity task) {
        List<TaskFocusRegionDTO> regions = JsonUtils.parse(task.getFocusRegionsJson(),
                new TypeReference<List<TaskFocusRegionDTO>>() { });
        return regions == null ? List.of() : regions;
    }

    /**
     * 字符串trim，空白转为null
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
     * 获取任务读取范围，null默认FULL
     * @param task 任务实体
     * @return TaskReadScope
     */
    private TaskReadScope readScope(TaskEntity task) {
        return task.getReadScope() == null ? TaskReadScope.FULL : TaskReadScope.valueOf(task.getReadScope());
    }

    /**
     * 校验文档类型合法性，不合法抛异常
     * @param documentType 文档类型编码
     * @return DocType枚举
     */
    private DocType requireDocumentType(Integer documentType) {
        DocType type = DocType.fromCode(documentType);
        if (type == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档类型无效");
        }
        return type;
    }

    /**
     * 构建任务编号：前缀+日期+任务id
     * @param taskId 任务id
     * @return taskNo
     */
    private String buildTaskNo(Long taskId) {
        return TaskConstant.TASK_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + taskId;
    }

    /**
     * 内部记录：封装读取范围和序列化后的关注区域json
     */
    private record FocusRegions(TaskReadScope scope, String json) {
    }

    /**
     * Feign返回结果包装工具，非成功/无data抛出业务异常
     * @param result feign远程调用返回Result
     * @return 取result.data()
     * @param <T> data类型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "文档服务调用失败" : result.message());
        }
        return result.data();
    }

    /**
     * 校验文档服务返回的空间权限结果。该契约成功时 data 为空，不能使用 requireData。
     */
    private void requirePermission(Long spaceId, String permissionCode) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, permissionCode);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "文档服务权限校验失败" : result.message());
        }
    }

    /**
     * 校验Agent文档访问范围配置，判断目标documentId是否在允许列表内
     * <p>agentExecutionProfileVO.docScope为空代表不做文档限制；配置异常抛出BAD_REQUEST；不在列表抛出FORBIDDEN。</p>
     * @param agentExecutionProfileVO Agent执行简介VO
     * @param documentId 待访问文档ID
     */
    private void requireDocumentScope(AgentExecutionProfileVO agentExecutionProfileVO, Long documentId) {
        // Agent执行简介文档服务为范围直接返回
        if (StringUtils.isBlank(agentExecutionProfileVO.documentScope())) {
            return;
        }
        try {
            // 解析Agent执行简介文档范围
            JsonNode scope = objectMapper.readTree(agentExecutionProfileVO.documentScope());
            // 获取文档Id列表
            JsonNode ids = scope.isArray() ? scope : scope.path("documentIds");
            boolean allowed = ids.isArray();
            // 查看【待访问文档ID】是否在【Agent】文档范围内
            if (allowed) {
                allowed = false;
                for (JsonNode id : ids) {
                    if (id.asLong() == documentId) {
                        allowed = true;
                        break;
                    }
                }
            }
            if (!allowed) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "文档不在 Agent 授权范围内");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 文档范围配置无效");
        }
    }
}
