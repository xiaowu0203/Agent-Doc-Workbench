package com.agentdoc.agent.service;

import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentOnlineOriginalTextMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentOnlineOriginalTextEntity;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.OnlineReportExecutionFactVO;
import com.agentdoc.common.feign.vo.OnlineReportUsageVO;
import java.time.LocalDateTime;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 报告用独立执行事实；批量查询不返回执行快照、Prompt或工具参数。 */
@Service
@RequiredArgsConstructor
public class OnlineReportExecutionFactsService {
    private final AgentExecutionMapper executions;
    private final AgentExecutionQueryService query;
    private final SpaceAccessService access;
    private final AgentOnlineOriginalTextMapper originals;
    public List<OnlineReportExecutionFactVO> facts(OnlineReportFactsQueryDTO request) {
        request.validate(); long space = OnlineProtocolUtils.id(request.spaceId());
        access.requirePermission(space, TASK_READ); access.requirePermission(space, EVALUATION_READ); access.requirePermission(space, USAGE_READ);
        var rows = executions.selectList(new LambdaQueryWrapper<AgentExecutionEntity>().in(AgentExecutionEntity::getWorkbenchTaskId,
                request.bindings().stream().map(binding -> OnlineProtocolUtils.id(binding.taskId())).toList())).stream()
                .collect(Collectors.toMap(AgentExecutionEntity::getWorkbenchTaskId, row -> row));
        var captured = originals.selectList(new LambdaQueryWrapper<AgentOnlineOriginalTextEntity>().select(AgentOnlineOriginalTextEntity.class, field -> !"original_text".equals(field.getColumn()))
                .in(AgentOnlineOriginalTextEntity::getTaskId, request.bindings().stream().map(binding -> OnlineProtocolUtils.id(binding.taskId())).toList())).stream()
                .collect(Collectors.toMap(AgentOnlineOriginalTextEntity::getTaskId, row -> row));
        return request.bindings().stream().map(binding -> {
            var row = rows.get(OnlineProtocolUtils.id(binding.taskId())); String hash = OnlineProtocolUtils.hash("online.binding", binding);
            if (row == null) { return new OnlineReportExecutionFactVO(binding.taskId(), binding.assignmentId(), hash, "ABSENT", null, null, null, null, null, null, null, null); }
            if (!Objects.equals(row.getSpaceId(), space) || !Objects.equals(row.getOnlineExperimentId(), OnlineProtocolUtils.id(request.experimentId()))
                    || !Objects.equals(row.getOnlineAssignmentId(), OnlineProtocolUtils.id(binding.assignmentId()))
                    || !Objects.equals(row.getAgentId(), OnlineProtocolUtils.id(binding.agentId())) || !hash.equals(row.getOnlineBindingHash())
                    || !Integer.valueOf(2).equals(row.getOnlineBindingSchemaVersion())) { throw new BusinessException(ErrorCode.CONFLICT, "执行报告身份不匹配"); }
            var original = captured.get(row.getWorkbenchTaskId());
            if (original != null) {
                var proof = new AgentOnlineOriginalTextVO(2, "AVAILABLE", original.getId().toString(), original.getTaskId().toString(), original.getId().toString(), original.getSpaceId().toString(),
                        original.getAgentId().toString(), original.getExperimentId().toString(), original.getAssignmentId().toString(), original.getBindingHash(), original.getContentHash(), original.getIdentityHash(), text(original.getCapturedAt()), null);
                if (!Objects.equals(original.getId(), row.getId()) || !Objects.equals(original.getSpaceId(), row.getSpaceId()) || !Objects.equals(original.getAgentId(), row.getAgentId())
                        || !Objects.equals(original.getAssignmentId(), row.getOnlineAssignmentId()) || !Objects.equals(original.getExperimentId(), row.getOnlineExperimentId())
                        || !Objects.equals(original.getBindingHash(), hash) || !Objects.equals(original.getIdentityHash(), OnlineOriginalTextUtils.identityHash(proof))) { throw new BusinessException(ErrorCode.CONFLICT, "原始捕获身份不匹配"); }
            }
            return new OnlineReportExecutionFactVO(binding.taskId(), binding.assignmentId(), hash, "AVAILABLE", row.getId().toString(), row.getStatus(),
                    text(row.getCreatedAt()), text(row.getStartedAt()), text(row.getFinishedAt()), OnlineReportUsageVO.from(query.tokenUsageOf(row)), original == null ? null : text(original.getCapturedAt()), original == null ? null : original.getIdentityHash());
        }).toList();
    }
    private static String text(LocalDateTime value) { return value == null ? null : value.toString(); }
}
