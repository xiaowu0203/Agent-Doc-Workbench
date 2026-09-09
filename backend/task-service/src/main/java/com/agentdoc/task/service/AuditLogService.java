package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.mapper.AuditLogMapper;
import com.agentdoc.task.pojo.entity.AuditLogEntity;
import com.agentdoc.task.pojo.param.AuditLogSearchParam;
import com.agentdoc.task.pojo.vo.AuditLogVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.AUDIT_READ;

/**
 * 追加型审计日志服务。业务代码只允许通过 insert 写入，不提供修改和删除能力。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final DocumentFeign documentFeign;
    private final AuthFeign authFeign;
    private final AgentFeign agentFeign;

    public void recordHuman(Long spaceId, AuditAction action, AuditTargetType targetType,
                            Long targetId, String detail) {
        record(spaceId, null, ActorType.HUMAN, AuthUtils.getUserIdOrException(),
                action, targetType, targetId, detail);
    }

    public void recordAgent(Long spaceId, Long taskId, Long agentId, AuditAction action,
                            AuditTargetType targetType, Long targetId, String detail) {
        record(spaceId, taskId, ActorType.AGENT, agentId, action, targetType, targetId, detail);
    }

    private void record(Long spaceId, Long taskId, ActorType actorType, Long actorId, AuditAction action,
                        AuditTargetType targetType, Long targetId, String detail) {
        auditLogMapper.insert(AuditLogEntity.create(
                spaceId, taskId, actorType, actorId, action, targetType, targetId, detail));
    }

    /**
     * 查询指定变更请求的追加式审计轨迹
     * 根据变更请求ID，按创建时间正序获取该变更请求下全部审计日志
     * @param changeRequestId 变更请求ID
     * @return 审计日志实体列表
     */
    public List<AuditLogEntity> listChangeRequestTrail(Long changeRequestId) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getTargetType, AuditTargetType.CHANGE_REQUEST.getCode())
                .eq(AuditLogEntity::getTargetId, changeRequestId)
                .orderByAsc(AuditLogEntity::getCreatedAt));
    }

    /**
     * 按空间、时间范围分页查询追加型审计日志
     * 支持按操作人类型、操作行为、目标类型、时间区间过滤；
     * 批量拉取用户/Agent名称做VO组装，返回分页结果
     * @param param 审计日志搜索入参
     * @return 分页审计日志VO
     */
    public PageVO<AuditLogVO> search(AuditLogSearchParam param) {
        // 参数合法性校验
        param.validate();
        // 校验当前用户拥有该空间审计日志读取权限
        requireAuditRead(param.getSpaceId());

        // 校验时间区间：起始时间不能晚于结束时间
        if (param.getCreatedFrom() != null && param.getCreatedTo() != null
                && !param.getCreatedFrom().isBefore(param.getCreatedTo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审计日志时间范围不合法");
        }
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getSpaceId, param.getSpaceId())
                .orderByDesc(AuditLogEntity::getCreatedAt)
                .orderByDesc(AuditLogEntity::getId);
        if (param.getActorType() != null) {
            wrapper.eq(AuditLogEntity::getActorType, param.getActorType());
        }
        if (param.getAction() != null && !param.getAction().isBlank()) {
            wrapper.eq(AuditLogEntity::getAction, param.getAction().trim());
        }
        if (param.getTargetType() != null && !param.getTargetType().isBlank()) {
            wrapper.eq(AuditLogEntity::getTargetType, param.getTargetType().trim());
        }
        if (param.getCreatedFrom() != null) {
            wrapper.ge(AuditLogEntity::getCreatedAt, param.getCreatedFrom());
        }
        if (param.getCreatedTo() != null) {
            wrapper.lt(AuditLogEntity::getCreatedAt, param.getCreatedTo());
        }

        // 执行分页查询
        Page<AuditLogEntity> page = auditLogMapper.selectPage(
                new Page<>(param.getPageNum(), param.getPageSize()), wrapper);

        // 批量远程拉取用户、Agent名称，避免循环feign调用
        Map<Long, String> userNames = fetchUserNames(page.getRecords());
        Map<Long, String> agentNames = fetchAgentNames(page.getRecords());

        // 实体转换VO，填充操作人展示名称
        List<AuditLogVO> records = page.getRecords().stream()
                .map(row -> AuditLogVO.from(row, actorName(row, userNames, agentNames)))
                .toList();
        return PageVO.of(records,
                page.getTotal(), param);
    }

    /**
     * 根据审计日志行，获取操作人展示名称
     * Agent类型返回Agent名称；人工用户返回用户昵称/用户名；无匹配返回默认文本
     * @param row 审计日志实体
     * @param userNames 用户ID-名称映射
     * @param agentNames AgentID-名称映射
     * @return 展示用操作人名称
     */
    private String actorName(AuditLogEntity row, Map<Long, String> userNames, Map<Long, String> agentNames) {
        if (ActorType.AGENT.getCode() == row.getActorType()) {
            return agentNames.getOrDefault(row.getActorId(), "Agent");
        }
        return userNames.getOrDefault(row.getActorId(), "用户");
    }

    /**
     * 批量拉取日志记录中涉及的人工用户名称
     * 过滤出HUMAN类型actorId，调用auth服务批量查询，构建id->名称映射
     * @param rows 审计日志实体列表
     * @return 用户ID -> 用户展示名称
     */
    private Map<Long, String> fetchUserNames(List<AuditLogEntity> rows) {
        List<Long> ids = rows.stream()
                .filter(row -> ActorType.HUMAN.getCode() == row.getActorType())
                .map(AuditLogEntity::getActorId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Result<List<UserRefVO>> result = authFeign.queryUsers(new UserBatchQueryDTO(ids));
        if (result == null || result.data() == null || result.data().isEmpty()) {
            return Map.of();
        }
        return result.data().stream().collect(Collectors.toMap(UserRefVO::id,
                user -> user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname(),
                (left, right) -> left));
    }

    /**
     * 批量拉取日志记录中涉及的Agent名称
     * 过滤出AGENT类型actorId，调用agent服务批量查询，构建id->名称映射
     * @param rows 审计日志实体列表
     * @return AgentID -> Agent名称
     */
    private Map<Long, String> fetchAgentNames(List<AuditLogEntity> rows) {
        List<Long> ids = rows.stream()
                .filter(row -> ActorType.AGENT.getCode() == row.getActorType())
                .map(AuditLogEntity::getActorId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Result<List<AgentRefVO>> result = agentFeign.queryAgentRefs(new AgentBatchQueryDTO(ids));
        if (result == null || result.data() == null || result.data().isEmpty()) {
            return Map.of();
        }
        return result.data().stream().collect(Collectors.toMap(AgentRefVO::id, AgentRefVO::name,
                (left, right) -> left));
    }

    /**
     * 校验空间审计日志读取权限
     * 调用文档服务校验当前用户是否拥有该空间 AUDIT_READ 权限，无权限抛出业务异常
     * @param spaceId 空间ID
     */
    private void requireAuditRead(Long spaceId) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, AUDIT_READ);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }
}
