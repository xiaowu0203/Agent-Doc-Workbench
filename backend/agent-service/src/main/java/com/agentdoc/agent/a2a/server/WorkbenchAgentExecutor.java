package com.agentdoc.agent.a2a.server;

import com.agentdoc.agent.execution.AgentExecutionApplicationService;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.springframework.stereotype.Component;

/**
 * A2A Agent执行器实现
 * <p>
 * 实现A2A协议定义的 {@link AgentExecutor} 接口，作为协议层与业务应用层之间的桥梁；
 * 本身不实现具体Agent执行逻辑，全部委托给 {@link AgentExecutionApplicationService} 完成；
 * 将A2A协议的入参对象 {@link RequestContext}、{@link AgentEmitter} 透传给业务服务，
 * 做到A2A协议内核和业务领域逻辑解耦。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class WorkbenchAgentExecutor implements AgentExecutor {

    /** Agent业务执行应用服务，承载文档协作、MCP工具调用、模型推理等核心业务逻辑 */
    private final AgentExecutionApplicationService executionService;

    /**
     * 启动/执行Agent任务
     * @param context A2A请求上下文，包含任务信息、鉴权、协议相关上下文
     * @param emitter 事件发射器，用于向外输出Agent增量事件、消息、状态变更
     * @throws A2AError A2A协议层面异常，会转换为A2A标准错误返回客户端
     */
    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        executionService.execute(context, emitter);
    }

    /**
     * 取消正在运行的Agent任务
     * @param context A2A请求上下文
     * @param emitter 事件发射器，可输出取消完成事件
     * @throws A2AError A2A协议层面异常
     */
    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        executionService.cancel(context, emitter);
    }
}
