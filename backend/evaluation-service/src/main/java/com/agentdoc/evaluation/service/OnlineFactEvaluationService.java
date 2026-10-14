package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.feign.dto.AgentEvaluationEvidenceQueryDTO;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluatorEngine;
import com.agentdoc.evaluation.evaluator.OnlineRuleContractValidator;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Map;
import java.util.Set;

import static com.agentdoc.common.enums.OnlineReasonCode.ONLINE_EVALUATION_IDENTITY_CONFLICT;
import static com.agentdoc.common.enums.OnlineReasonCode.ONLINE_FACT_UNKNOWN;
import static com.agentdoc.common.enums.OnlineReasonCode.ONLINE_EVALUATION_SUBJECT_NOT_READY;

/** 复用既有确定性规则；先求证当前权限与冻结绑定，不读取正文或模型配置。 */
@Service
@RequiredArgsConstructor
public class OnlineFactEvaluationService {
    private static final Set<String> TASK_TERMINAL = Set.of("COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT");
    private static final Set<String> EXECUTION_TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELED", "TIMED_OUT");
    private static final Set<String> FAILURE = Set.of("FAILED", "TIMED_OUT");
    private final OnlineExecutionAuthority authority;
    private final OnlineTaskFeign tasks;
    private final AgentFeign agents;
    private final OnlineAgentFeign onlineAgents;
    private final DeterministicEvaluatorEngine evaluator;

    public DeterministicEvaluationOutcome evaluate(String key, String config, String expected, OnlineAssignmentEntity assignment) {
        var binding = authority.binding(assignment);
        var query = new OnlineReportFactsQueryDTO(binding.spaceId(), binding.experimentId(), List.of(binding));
        var taskRows = data(tasks.reportFacts(query));
        var agentRows = data(onlineAgents.reportFacts(query));
        if (taskRows.size() != 1 || agentRows.size() != 1) { throw conflict(); }
        var task = taskRows.getFirst(); var agent = agentRows.getFirst();
        if (!binding.taskId().equals(task.taskId()) || !binding.assignmentId().equals(task.assignmentId())
                || !assignment.getBindingHash().equals(task.bindingHash())
                || !binding.taskId().equals(agent.taskId()) || !binding.assignmentId().equals(agent.assignmentId())
                || !assignment.getBindingHash().equals(agent.bindingHash())
                || !assignment.getExecutionId().toString().equals(task.executionId())
                || !assignment.getExecutionId().toString().equals(agent.executionId())) { throw conflict(); }
        if (!"AVAILABLE".equals(task.state()) || !"AVAILABLE".equals(agent.state())) { throw unavailable(); }
        // 旧引擎的 false 是有效规则失败；未收敛或取消不能交给它生成失败指标。
        boolean definiteFailure = FAILURE.contains(Objects.toString(task.status(), "")) || FAILURE.contains(Objects.toString(agent.status(), ""));
        boolean ready = OnlineRuleContractValidator.TASK.equals(key)
                ? definiteFailure || "COMPLETED".equals(task.status()) && "COMPLETED".equals(agent.status())
                : TASK_TERMINAL.contains(Objects.toString(task.status(), "")) && EXECUTION_TERMINAL.contains(Objects.toString(agent.status(), ""));
        if (!ready) {
            return new DeterministicEvaluationOutcome(EvaluationResultStatus.SKIPPED, ONLINE_EVALUATION_SUBJECT_NOT_READY.name(), "{}", List.of(), List.of());
        }
        // 批量投影仅含运行事实；先经过上面两个带当前权限/绑定核验的来源，再读取 Trace 元数据。
        var auditRows = data(agents.queryEvaluationEvidence(new AgentEvaluationEvidenceQueryDTO(List.of(assignment.getTaskId()))));
        if (auditRows.size() != 1) { throw conflict(); }
        var audit = auditRows.getFirst();
        if (!Objects.equals(assignment.getTaskId(), audit.taskId()) || !Objects.equals(assignment.getExecutionId(), audit.executionId())
                || !Objects.equals(agent.status(), audit.status())) { throw conflict(); }
        var usage = task.usage();
        if (usage != null && !Objects.equals(assignment.getExecutionId(), usage.executionId())) { throw conflict(); }
        if (OnlineRuleContractValidator.AUDIT.equals(key)
                && (usage == null || usage.inputTokens() == null && usage.outputTokens() == null)) { throw unavailable(); }
        var evidence = new EvaluationEvidenceBundleVO(assignment.getTaskId(), OnlineProtocolUtils.id(binding.spaceId()), null,
                task.status(), null, audit.executionId(), audit.status(), audit.traceId(), audit.spanId(), audit.startedAt(), audit.finishedAt(),
                usage == null ? null : usage.inputTokens(), usage == null ? null : usage.cachedInputTokens(), usage == null ? null : usage.outputTokens(),
                null, usage == null ? null : usage.currency(), null, audit.toolCallCount(), audit.failedToolCallCount(), audit.externalMcpCallCount(), 0, List.of());
        var outcome = evaluator.evaluate(key, config, expected, evidence);
        // Trace 事实按固定字段顺序编码；时间使用 ISO 文本，不依赖应用 ObjectMapper 的模块配置。
        String factsHash = OnlineProtocolUtils.hash("online.evaluation-facts", Map.of("task", task, "execution", agent,
                "audit", Arrays.asList(audit.taskId(), audit.executionId(), audit.status(), audit.traceId(), audit.spanId(),
                        Objects.toString(audit.startedAt(), null), Objects.toString(audit.finishedAt(), null),
                        audit.toolCallCount(), audit.failedToolCallCount(), audit.externalMcpCallCount())));
        var references = outcome.evidence().stream().map(ref -> new EvidenceReferenceValue(ref.referenceKey(),ref.evidenceType(),ref.businessId(),
                OnlineProtocolUtils.hash("online.fact-evidence", Map.of("factsHash",factsHash,"evidenceType",ref.evidenceType().name(),"businessId",ref.businessId())),
                ref.summary(),ref.locatorJson())).toList();
        return new DeterministicEvaluationOutcome(outcome.status(),outcome.summaryCode(),outcome.detailsJson(),outcome.metrics(),references);
    }

    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw unavailable(); }
        return result.data();
    }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.CONFLICT, ONLINE_EVALUATION_IDENTITY_CONFLICT.name()); }
    private static BusinessException unavailable() { return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, ONLINE_FACT_UNKNOWN.name()); }
}
