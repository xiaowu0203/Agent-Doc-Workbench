package com.agentdoc.task.service;

import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.OnlineReportTaskFactVO;
import com.agentdoc.common.feign.vo.OnlineReportUsageVO;
import java.time.LocalDateTime;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 报告只读事实按批加载；账本费用从冻结字段取得，不读取当前模型价格。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineReportFactsService {
    private final TaskMapper tasks;
    private final TokenUsageDetailMapper ledger;
    private final DocumentFeign documents;

    public List<OnlineReportTaskFactVO> facts(OnlineReportFactsQueryDTO request) {
        request.validate(); long space = OnlineProtocolUtils.id(request.spaceId());
        permitted(documents.checkSpacePermission(space, TASK_READ));
        permitted(documents.checkSpacePermission(space, EVALUATION_READ));
        permitted(documents.checkSpacePermission(space, USAGE_READ));
        var ids = request.bindings().stream().map(binding -> OnlineProtocolUtils.id(binding.taskId())).toList();
        var rows = tasks.selectList(new LambdaQueryWrapper<TaskEntity>().in(TaskEntity::getId, ids)).stream()
                .collect(Collectors.toMap(TaskEntity::getId, row -> row));
        var accounts = ledger.selectList(new LambdaQueryWrapper<TokenUsageDetailEntity>().in(TokenUsageDetailEntity::getTaskId, ids)).stream()
                .collect(Collectors.toMap(TokenUsageDetailEntity::getTaskId, row -> row));
        return request.bindings().stream().map(binding -> {
            String hash = OnlineProtocolUtils.hash("online.binding", binding); var row = rows.get(OnlineProtocolUtils.id(binding.taskId()));
            if (row == null) { return new OnlineReportTaskFactVO(binding.taskId(), binding.assignmentId(), hash, "ABSENT", null, null, null, null, null, null, null, null, null); }
            TaskOnlineObservationService.requireIdentity(row, binding, hash);
            var account = accounts.get(row.getId()); OnlineReportUsageVO usage = null; String ledgerId = null, ledgerHash = null;
            if (account != null) {
                if (!Objects.equals(account.getExecutionId(), row.getAgentExecutionId()) || !Objects.equals(account.getSpaceId(), row.getSpaceId())
                        || !Objects.equals(account.getAgentId(), row.getAgentId())) { throw new BusinessException(ErrorCode.CONFLICT, "账本身份不匹配"); }
                usage = OnlineReportUsageVO.from(new AgentExecutionTokenUsageVO(account.getExecutionId(), account.getModelId(), account.getModelConfigVersion(),
                        account.getInputPricePerMillion(), account.getOutputPricePerMillion(), account.getCurrency(), account.getPricingSchemaVersion(),
                        account.getPricingCapturedAt(), account.getInputTokens(), account.getInputTokensEstimated(), account.getCachedInputTokens(),
                        account.getCachedInputTokensEstimated(), account.getOutputTokens(), account.getOutputTokensEstimated()));
                ledgerId = account.getId().toString(); ledgerHash = OnlineProtocolUtils.hash("online.report-ledger", usage);
            }
            return new OnlineReportTaskFactVO(binding.taskId(), binding.assignmentId(), hash, "AVAILABLE", TaskStatus.fromCode(row.getStatus()).name(),
                    row.getAgentExecutionId() == null ? null : row.getAgentExecutionId().toString(), text(row.getCreatedAt()), text(row.getStartTime()), text(row.getEndTime()), ledgerId, ledgerHash,
                    account == null ? null : text(account.getCreatedAt()), usage);
        }).toList();
    }
    private static String text(LocalDateTime value) { return value == null ? null : value.toString(); }
    private static void permitted(Result<?> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) { throw new BusinessException(ErrorCode.FORBIDDEN, "报告事实读取无权限"); }
    }
}
