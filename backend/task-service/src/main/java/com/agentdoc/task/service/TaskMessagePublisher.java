package com.agentdoc.task.service;

/**
 * 任务消息发布端口，便于业务服务与 RabbitMQ 实现解耦并可单元测试。
 */
public interface TaskMessagePublisher {

    void publish(Long taskId);

    /**
     * 发布需要后台授权上下文的任务消息。
     *
     * @param taskId 待执行任务ID
     * @param dispatchAuthorization 仅用于实际派发前签发Task Capability的后台授权令牌
     */
    void publish(Long taskId, String dispatchAuthorization);
}
