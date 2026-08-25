package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.tool.TokenUsageEstimator;
import com.agentdoc.agent.pojo.entity.AgentEntity;

import java.util.function.BooleanSupplier;
import com.agentdoc.common.pojo.TokenValue;

/**
 * Spring‑AI Alibaba桥接运行时的控制器
 * <p>
 * 用于包装Spring‑AI Alibaba原生Agent执行流程，在外部做防护逻辑：
 * 取消信号检测、最大迭代次数熔断、Token预算管控、模型调用状态、用量统计归集。
 * 原生Spring‑AI Alibaba Agent没有内置这些控制能力，由本类做外围拦截。
 * 抛出的异常为项目自定义业务异常：{@link AgentExecutionCanceledException}、{@link AgentExecutionLimitExceededException}。
 * </p>
 */
public final class AlibabaRuntimeControl {
    /** 任务取消信号检测器 */
    private final BooleanSupplier cancelRequested;
    /** Token总预算上限，null代表不限制 */
    private final Long tokenBudget;
    /** Agent最大模型调用迭代次数，防止无限工具调用死循环 */
    private final int maxIterations;
    /** Token用量估算器，用于补齐厂商未返回的token统计 */
    private final TokenUsageEstimator estimator;

    /** 累计全流程Token用量 */
    private TokenUsage usage = TokenUsage.unavailable();
    /** 已知输入token累加值，仅取有效返回值，未知按0处理 */
    private long knownInputTokens;
    /** 已知输出token累加值，仅取有效返回值，未知按0处理 */
    private long knownOutputTokens;
    /** 已发起模型调用次数 */
    private int modelCalls;
    /** 是否有正在执行中的模型调用 */
    private boolean modelInFlight;
    /** 是否已经记录过至少一轮模型用量 */
    private boolean usageRecorded;

    /**
     * 构造桥接运行时控制器
     * @param agent Agent实体，读取配置的最大迭代次数
     * @param requestedBudget token预算上限，允许null无限制
     * @param cancelRequested 取消信号判断器
     * @param estimator token用量估算器
     */
    public AlibabaRuntimeControl(AgentEntity agent, Long requestedBudget,
                                 BooleanSupplier cancelRequested, TokenUsageEstimator estimator) {
        this.cancelRequested = cancelRequested;
        this.tokenBudget = requestedBudget;
        this.maxIterations = agent.getMaxIterations() == null
                ? com.agentdoc.agent.constant.AgentConstant.DEFAULT_MAX_ITERATIONS : agent.getMaxIterations();
        this.estimator = estimator;
    }

    /**
     * 发起模型调用前执行校验：检测取消、预算校验、迭代次数保护
     * 超过最大迭代次数抛出 {@link AgentExecutionLimitExceededException}
     */
    public void beforeModel() {
        checkCanceled();
        ensureBudgetAvailableForNextModel();
        if (modelCalls++ > maxIterations) throw new AgentExecutionLimitExceededException(maxIterations);
        modelInFlight = true;
    }

    /**
     * 模型调用正常完成：结束调用状态，归集本轮用量，检测取消与预算
     * @param turnUsage 本轮模型调用token用量
     */
    public void afterModel(TokenUsage turnUsage) {
        finishModelCall();
        recordUsage(turnUsage);
        checkCanceled();
        checkBudget();
    }

    /**
     * 模型调用被取消场景：仅结束状态并记录用量，不再做后续业务校验
     * @param turnUsage 本轮token用量
     */
    public void afterModelCanceled(TokenUsage turnUsage) {
        finishModelCall();
        recordUsage(turnUsage);
    }

    /**
     * 记录单轮模型调用token用量，累加至总用量
     * @param turnUsage 单轮用量
     */
    private void recordUsage(TokenUsage turnUsage) {
        knownInputTokens += knownValue(turnUsage.input());
        knownOutputTokens += knownValue(turnUsage.output());
        usage = usageRecorded ? usage.add(turnUsage) : turnUsage;
        usageRecorded = true;
    }

    /**
     * 模型调用发生异常失败：仅清理模型执行中标记，不统计用量
     */
    public void afterModelFailure() {
        finishModelCall();
    }

    /**
     * 工具执行前检测任务取消信号
     */
    public void beforeTool() {
        checkCanceled();
    }

    /**
     * 工具执行完成后检测任务取消信号
     */
    public void afterTool() {
        checkCanceled();
    }

    /**
     * 检测是否收到取消信号，收到抛出 {@link AgentExecutionCanceledException}
     */
    public void checkCanceled() {
        if (cancelRequested.getAsBoolean())
            throw new AgentExecutionCanceledException();
    }

    /**
     * 获取累计全流程token用量
     * @return 汇总TokenUsage
     */
    public TokenUsage usage() { return usage; }

    public TokenUsageEstimator estimator() { return estimator; }

    int maxIterations() { return maxIterations; }

    boolean modelInFlight() { return modelInFlight; }

    /**
     * 清理模型调用进行中标记
     */
    private void finishModelCall() { modelInFlight = false; }

    /**
     * 校验当前总token是否已经超过预算，超限抛出异常
     * <p>
     * 说明：仅使用已知返回的token做下界熔断；缺失字段由上层Collector用估算器补齐；
     * 完整权威核算交给task‑service。
     * </p>
     */
    private void checkBudget() {
        if (tokenBudget == null || !usageRecorded)
            return;
        long knownTokens = knownInputTokens + knownOutputTokens;
        if (knownTokens > tokenBudget)
            throw new IllegalStateException("Agent Token 预算已超限");
    }

    /**
     * 发起下一次模型前校验预算，判断预算是否已经耗尽
     */
    private void ensureBudgetAvailableForNextModel() {
        checkBudget();
        if (tokenBudget != null && usageRecorded && knownInputTokens + knownOutputTokens >= tokenBudget) {
            throw new IllegalStateException("Agent Token 预算已耗尽");
        }
    }

    /**
     * 取出TokenValue的有效值；不可用时返回0
     * @param value token包装对象
     * @return 原始数值或者0
     */
    private long knownValue(TokenValue value) {
        return value.available() ? value.value() : 0L;
    }
}
