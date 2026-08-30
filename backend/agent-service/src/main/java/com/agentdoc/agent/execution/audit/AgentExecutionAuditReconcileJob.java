package com.agentdoc.agent.execution.audit;

import com.agentdoc.agent.constant.AgentConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Agent执行审计补偿[定时任务]
 * <p>
 * 场景：Agent执行过程中服务崩溃、线程异常中断、流程异常终止，审计记录状态无法正常流转为完成/失败，
 * 会产生一批【已开始】但永远不会结束的僵死审计记录。
 * 本定时任务定期扫描，将超时僵死的工具调用、模型调用审计记录强制标记为失败，做状态补偿。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutionAuditReconcileJob {

    /**
     * 补偿宽限时间，单位秒：最大执行超时之后额外等待的缓冲时间，避免正常慢执行被误杀
     * 5分钟，300秒
     */
    private static final int RECONCILE_GRACE_SECONDS = 300;

    /** 工具调用审计业务服务，处理工具调用僵死记录补偿 */
    private final AgentExecutionToolAuditService toolAuditService;
    /** 模型调用审计业务服务，处理模型调用僵死记录补偿 */
    private final AgentExecutionModelCallAuditService modelCallAuditService;

    /**
     * 审计状态补偿调度入口
     * <p>补偿逻辑：
     * 1. 计算截止时间点：当前时间 - (Agent最大执行超时时间 + 宽限秒数)
     * 2. 将【状态为已开始、开始时间早于截止时间】的僵死审计记录强制更新为失败
     * 3. 打印告警日志，输出本次补偿的工具调用、模型调用条数
     * </p>
     * <p>
     * 宽限时间作用：防止业务正常慢调用还在执行中，就被定时任务误标记失败；
     * 只有超过【最大执行时长+缓冲】仍未结束，才判定为僵死任务。
     * </p>
     */
    @Scheduled(fixedDelayString = "${agent-doc.execution-audit.reconcile-delay-ms:300000}")
    public void reconcile() {
        // 计算时间阈值：早于该时间点且仍处于“已开始”状态，视为僵死任务
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(
                AgentConstant.MAX_EXECUTION_TIMEOUT_SECONDS + RECONCILE_GRACE_SECONDS);

        // 补偿僵死的工具调用审计记录，返回处理条数（状态更新为调用失败）
        int toolCalls = toolAuditService.failStaleStarted(cutoff);
        // 补偿僵死的模型调用审计记录，返回处理条数（状态更新为调用失败或审计结束信息缺失）
        int modelCalls = modelCallAuditService.failStaleStarted(cutoff);

        // 有补偿记录输出warn告警日志，便于监控告警接入
        if (toolCalls > 0 || modelCalls > 0) {
            log.warn("已补偿超时未结束的执行审计: toolCalls={}, modelCalls={}", toolCalls, modelCalls);
        }
    }
}
