package com.agentdoc.task.service;

/**
 * 任务消息发布端口，便于业务服务与 RabbitMQ 实现解耦并可单元测试。
 */
public interface TaskMessagePublisher {

    void publish(Long taskId);
}
