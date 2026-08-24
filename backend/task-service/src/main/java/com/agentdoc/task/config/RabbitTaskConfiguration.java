package com.agentdoc.task.config;

import com.agentdoc.task.mq.RabbitTaskMessagePublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 任务队列配置
 * <p>
 * 任务执行消息，配置普通业务队列 + 死信队列DLQ
 * 当消息超时、被reject、nack且不重回队列时，消息会路由至死信交换机，进入死信队列
 * </p>
 * 业务队列：接收任务执行消息，消费者消费执行Agent任务
 * 死信队列：存放处理失败、异常的任务消息，用于后续排查、补偿、人工处理
 */
@Configuration
public class RabbitTaskConfiguration {

    /** 业务任务队列名称：接收task.execute任务执行消息 */
    public static final String QUEUE = "agent-doc-workbench.task.execute";
    /** 死信交换机名称，普通队列消息失败会转发到此交换机 */
    public static final String DLX = "agent-doc-workbench.task.dlx";
    /** 死信队列名称：存放处理失败的死信消息 */
    public static final String DLQ = "agent-doc-workbench.task.dead";

    /**
     * 任务业务直连交换机
     * @return DirectExchange 直连交换机，根据routing-key精确匹配绑定队列
     * 参数说明：name=交换机名称，durable=true持久化，autoDelete=false不自动删除
     */
    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(RabbitTaskMessagePublisher.EXCHANGE, true, false);
    }

    /**
     * 死信交换机(DLX)
     * <p>
     * 普通队列配置死信后，消息变成死信时会被投递到该交换机，再通过routing-key路由到死信队列
     * </p>
     * @return DirectExchange 死信直连交换机
     */
    @Bean
    public DirectExchange taskDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    /**
     * 业务任务队列
     * <p>
     * 配置持久化，同时指定死信交换机与死信路由key
     * 当消息出现：消息过期、消费者nack/reject且requeue=false，消息会转发到 DLX + routingKey=task.dead
     * </p>
     * @return 任务执行队列Bean
     */
    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey("task.dead")
                .build();
    }

    /**
     * 死信队列 DLQ
     * 接收业务队列投递过来的失败消息，持久化保存，用于异常任务复盘与补偿处理
     * @return 死信队列Bean
     */
    @Bean
    public Queue taskDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    /**
     * 业务队列绑定：将任务队列绑定到业务交换机，使用指定路由key
     * 生产者发送消息到 taskExchange，携带ROUTING_KEY，消息进入taskQueue业务队列
     * @return Binding 绑定关系Bean
     */
    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(taskQueue()).to(taskExchange()).with(RabbitTaskMessagePublisher.ROUTING_KEY);
    }

    /**
     * 死信队列绑定：死信交换机与死信队列绑定
     * 死信交换机收到 routingKey=task.dead 的消息，转发进入死信队列taskDeadLetterQueue
     * @return Binding 死信绑定关系Bean
     */
    @Bean
    public Binding taskDeadLetterBinding() {
        return BindingBuilder.bind(taskDeadLetterQueue()).to(taskDeadLetterExchange()).with("task.dead");
    }
}
