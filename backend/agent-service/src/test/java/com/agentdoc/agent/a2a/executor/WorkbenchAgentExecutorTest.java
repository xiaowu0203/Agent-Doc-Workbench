package com.agentdoc.agent.a2a.executor;

import com.agentdoc.agent.execution.application.AgentExecutionApplicationService;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 初始输入仅由新任务投递保存，不能借续发或取消回填旧记录。 */
class WorkbenchAgentExecutorTest {

    private final AgentExecutionApplicationService application = mock(AgentExecutionApplicationService.class);
    private final RequestContext context = mock(RequestContext.class);
    private final AgentEmitter emitter = mock(AgentEmitter.class);
    private final WorkbenchAgentExecutor executor = new WorkbenchAgentExecutor(application);

    @Test
    void newTaskPublishesOriginalInputBeforeExecution() throws Exception {
        Message initial = Message.builder().messageId("test-initial").role(Message.Role.ROLE_USER)
                .parts(new TextPart("test-instruction")).build();
        when(context.getMessage()).thenReturn(initial);
        when(emitter.taskBuilder()).thenReturn(Task.builder().id("test-a2a").contextId("test-context"));

        executor.execute(context, emitter);

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        var order = inOrder(emitter, application);
        order.verify(emitter).addTask(saved.capture());
        order.verify(application).execute(context, emitter);
        assertThat(saved.getValue().id()).isEqualTo("test-a2a");
        assertThat(saved.getValue().contextId()).isEqualTo("test-context");
        assertThat(saved.getValue().status().state()).isEqualTo(TaskState.TASK_STATE_SUBMITTED);
        assertThat(saved.getValue().history()).containsExactly(initial);
    }

    @Test
    void existingTaskDoesNotReseedMissingHistory() throws Exception {
        when(context.getTask()).thenReturn(Task.builder().id("test-existing").contextId("test-context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build());

        executor.execute(context, emitter);

        verify(emitter, never()).taskBuilder();
        verify(emitter, never()).addTask(any());
        verify(application).execute(context, emitter);
    }

    @Test
    void cancelDoesNotSeedInput() throws Exception {
        executor.cancel(context, emitter);

        verify(emitter, never()).taskBuilder();
        verify(emitter, never()).addTask(any());
        verify(application).cancel(context, emitter);
    }
}
