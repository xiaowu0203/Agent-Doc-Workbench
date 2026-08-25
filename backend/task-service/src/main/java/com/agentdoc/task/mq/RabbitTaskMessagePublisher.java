package com.agentdoc.task.mq;

import com.agentdoc.task.service.TaskMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Agent任务消息发布器，用于发送任务执行MQ消息。
 * <p>
 * 向交换机 {@link #EXCHANGE}、路由键 {@link #ROUTING_KEY} 投递任务执行消息；
 * 消费者监听该队列，收到消息后执行Agent后台异步任务。
 * </p>
 * <p>注意：
 * <ul>
 * <li>当前仅传递 taskId；MQ消息为异步线程，消费者侧不会继承Web请求的ThreadLocal上下文；</li>
 * <li>如果消费者需要Agent任务能力令牌，<b>不能依靠Feign自动透传</b>；需要发送方把令牌随消息体一起序列化存入MQ，消费端手动设置 {@link com.agentdoc.common.context.TaskCapabilityContext}，务必 try‑finally 调用 clear() 清理上下文。</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RabbitTaskMessagePublisher implements TaskMessagePublisher {
    /** 任务交换机名称 */
    public static final String EXCHANGE = "agent-doc-workbench.task";
    /** 任务执行路由key */
    public static final String ROUTING_KEY = "task.execute";

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布任务执行消息
     * @param taskId 待执行的任务ID
     */
    @Override
    public void publish(Long taskId) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, taskId);
    }
}
