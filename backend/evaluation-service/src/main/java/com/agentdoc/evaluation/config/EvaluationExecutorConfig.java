package com.agentdoc.evaluation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 确定性 Evaluator 独立有界线程池配置类
 * <p>
 * 特点：核心线程数 = 最大线程数，为固定大小线程池；任务队列有界，拒绝策略为 AbortPolicy，
 * 任务堆积时直接抛出拒绝异常，便于上层感知流量过载，保证评估任务执行确定性。
 * </p>
 */
@Configuration
public class EvaluationExecutorConfig {

    /**
     * 构建 Evaluator 评估任务专用线程池 Bean
     *
     * @param properties 评估运行时配置参数（并发数、队列容量等）
     * @return 评估任务线程池实例，Bean名称：evaluationExecutor
     */
    @Bean("evaluationExecutor")
    public ThreadPoolTaskExecutor evaluationExecutor(EvaluationRuntimeProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数，等同于最大线程数，固定线程模型，无扩容缩容
        executor.setCorePoolSize(properties.getWorkerConcurrency());
        // 最大线程数，和核心线程保持一致，固定线程池
        executor.setMaxPoolSize(properties.getWorkerConcurrency());
        // 有界任务队列容量，防止任务无限堆积导致OOM
        executor.setQueueCapacity(properties.getQueueCapacity());
        // 线程名前缀，方便日志排查：evaluation-worker-xxx
        executor.setThreadNamePrefix("evaluation-worker-");
        // 拒绝策略：AbortPolicy，队列满时直接抛出RejectedExecutionException，不丢弃任务、不阻塞调用方
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化线程池，创建核心线程
        executor.initialize();
        return executor;
    }
}
