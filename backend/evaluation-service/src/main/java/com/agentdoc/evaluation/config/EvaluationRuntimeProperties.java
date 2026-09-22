package com.agentdoc.evaluation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Evaluation worker 与 reconciliation 的可部署运行参数。
 * <p>
 * 包含评估任务线程池、任务超时、对账补偿批处理与分布式锁相关配置
 * </p>
 */
@ConfigurationProperties(prefix = "agent-doc.evaluation.runtime")
@Data
public class EvaluationRuntimeProperties {
    /** 是否允许创建新的 EvaluationRun。 */
    private boolean runCreationEnabled = true;

    /** 是否启用后台 reconciliation 推进。 */
    private boolean reconciliationEnabled = true;

    /**
     * Worker 并发数，对应评估线程池核心/最大线程数，默认 4
     */
    private int workerConcurrency = 4;

    /**
     * 评估任务有界队列容量，默认 100，队列满触发拒绝策略
     */
    private int queueCapacity = 100;

    /**
     * 单次评估任务执行超时时间，单位毫秒，默认 5000ms
     */
    private long evaluatorTimeoutMillis = 5000;

    /**
     * Reconciliation 对账补偿单次批量处理条数，默认 20
     */
    private int reconciliationBatchSize = 20;

    /**
     * 连续对账基础设施错误达到该次数后暂停 Run，默认 3
     */
    private int maxConsecutiveReconciliationFailures = 3;

    /**
     * Run 停留在 DISPATCHING 超过该时长后转为可恢复暂停，单位秒。
     */
    private long dispatchStaleSeconds = 60;

    /**
     * Reconciliation 分布式锁持有时长，单位秒，默认30s，防止任务重复执行
     */
    private long reconciliationLockSeconds = 30;
}
