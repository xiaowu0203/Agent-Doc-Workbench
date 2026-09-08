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

    /** 按创建时间读取指定变更请求的追加型轨迹。 */
    public List<AuditLogEntity> listChangeRequestTrail(Long changeRequestId) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getTargetType, AuditTargetType.CHANGE_REQUEST.getCode())
                .eq(AuditLogEntity::getTargetId, changeRequestId)
                .orderByAsc(AuditLogEntity::getCreatedAt));
    }

    /**
     * 按空间和时间范围分页读取追加型审计日志。
     */
    public PageVO<AuditLogVO> search(AuditLogSearchParam param) {
        param.validate();
        requireAuditRead(param.getSpaceId());
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
        Page<AuditLogEntity> page = auditLogMapper.selectPage(
                new Page<>(param.getPageNum(), param.getPageSize()), wrapper);
        Map<Long, String> userNames = fetchUserNames(page.getRecords());
        Map<Long, String> agentNames = fetchAgentNames(page.getRecords());
        List<AuditLogVO> records = page.getRecords().stream()
                .map(row -> AuditLogVO.from(row, actorName(row, userNames, agentNames)))
                .toList();
        return PageVO.of(records,
                page.getTotal(), param);
    }

    private String actorName(AuditLogEntity row, Map<Long, String> userNames, Map<Long, String> agentNames) {
        if (ActorType.AGENT.getCode() == row.getActorType()) {
            return agentNames.getOrDefault(row.getActorId(), "Agent");
        }
        return userNames.getOrDefault(row.getActorId(), "用户");
    }

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

    private void requireAuditRead(Long spaceId) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, AUDIT_READ);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }
}
