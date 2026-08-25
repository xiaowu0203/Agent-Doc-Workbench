package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.BooleanSupplier;

/** 为统一工具循环增加工具调用前后的任务取消检查。 */
public class CancellationAwareToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final BooleanSupplier cancelRequested;

    public CancellationAwareToolCallback(ToolCallback delegate, BooleanSupplier cancelRequested) {
        this.delegate = delegate;
        this.cancelRequested = cancelRequested;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String input) {
        requireNotCanceled();
        String result = delegate.call(input);
        requireNotCanceled();
        return result;
    }

    @Override
    public String call(String input, ToolContext toolContext) {
        requireNotCanceled();
        String result = delegate.call(input, toolContext);
        requireNotCanceled();
        return result;
    }

    private void requireNotCanceled() {
        if (cancelRequested.getAsBoolean()) {
            throw new AgentExecutionCanceledException();
        }
    }
}
