package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.SpaceBudgetVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.param.TaskActivitySearchParam;
import com.agentdoc.task.pojo.vo.TaskActivityVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.pojo.vo.TaskStatsVO;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_TERMINATE;

/**
 * 任务业务服务
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
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
     * <li>Token预算计算：任务传入预算优先，其次Agent默认预算，再取空间预算做min约束；预算小于1抛出参数异常；</li>
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
        // 根据agentId远程调用查询Agent信息
        AgentExecutionProfileVO agent = requireData(agentFeign.getExecutionProfile(dto.agentId()));

        // 校验Agent是否启用、文档是否匹配
        if (!agent.enabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已禁用");
        }
        if (!document.spaceId().equals(agent.spaceId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 与文档不属于同一空间");
        }

        // 校验Agent文档访问范围配置，判断目标documentId是否在允许列表内
        requireDocumentScope(agent, document.documentId());
        // 获取任务预算
        Long budget = dto.tokenBudget() == null ? agent.tokenBudget() : dto.tokenBudget();
        // 查询空间 Agent 执行预算
        SpaceBudgetVO spaceBudget = requireData(documentFeign.getSpaceExecutionBudget(document.spaceId()));
        if (spaceBudget.tokenBudget() != null) {
            // 取最小预算
            budget = budget == null ? spaceBudget.tokenBudget() : Math.min(budget, spaceBudget.tokenBudget());
        }
        if (budget != null && budget < TaskConstant.MIN_TOKEN_BUDGET) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务 Token 预算必须大于 0");
        }

        // 任务落库
        TaskEntity entity = dto.toEntity(document.spaceId(), budget, agent.configVersion(), userId);
        taskMapper.insert(entity);

        // 生成任务能力令牌，并更新任务记录
        try {
            List<String> actions = List.of(
                    JwtConstant.ACTION_READ_FRAGMENT,
                    JwtConstant.ACTION_WRITE_DRAFT,
                    JwtConstant.ACTION_CREATE_CHANGE_REQUEST);
            // 调用auth‑service申请任务短时能力JWT令牌
            String capability = requireData(authFeign.issueTaskCapability(
                    new TaskCapabilityIssueDTO(entity.getId(), agent.agentId(), document.spaceId(),
                            document.documentId(), actions)));
            // 令牌加密存储，不在数据库留存明文
            entity.setCapabilityToken(cryptoService.encrypt(capability));
            // 更新任务
            taskMapper.updateById(entity);
            // 投递MQ，触发异步任务消费执行
            messagePublisher.publish(entity.getId());
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
     * 分页查询空间下任务列表
     * @param spaceId 空间ID
     * @param pageParam 分页参数
     * @return 分页任务VO
     */
    public PageVO<TaskVO> list(Long spaceId, PageParam pageParam) {
        // Feign校验当前用户在该空间具备【读取】权限
        requirePermission(spaceId, TASK_READ);
        // 分页校验
        pageParam.validate();
        Page<TaskEntity> page = taskMapper.selectPage(new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getSpaceId, spaceId)
                        .orderByDesc(TaskEntity::getCreatedAt));
        return PageVO.of(page.getRecords().stream().map(TaskVO::from).toList(), page.getTotal(), pageParam);
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
        Page<TaskEntity> page = taskMapper.selectPage(
                new Page<>(pageParam.getPageNum(), pageParam.getPageSize()),
                new LambdaQueryWrapper<TaskEntity>()
                        .eq(TaskEntity::getSpaceId, param.spaceId())
                        .orderByDesc(TaskEntity::getLastHeartbeatAt)
                        .orderByDesc(TaskEntity::getEndTime)
                        .orderByDesc(TaskEntity::getStartTime)
                        .orderByDesc(TaskEntity::getCreatedAt));
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

    private String displayName(UserRefVO user) {
        if (user == null) {
            return null;
        }
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }

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
