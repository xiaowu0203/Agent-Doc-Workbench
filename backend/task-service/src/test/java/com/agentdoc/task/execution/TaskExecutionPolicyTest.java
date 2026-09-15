package com.agentdoc.task.execution;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskExecutionPolicyTest {

    @Test
    void acceptsOnlyPhaseOneLiveLineages() {
        for (TaskLineageType type : new TaskLineageType[]{
                TaskLineageType.ORIGINAL, TaskLineageType.RERUN, TaskLineageType.REVIEW_REWORK}) {
            assertThatCode(() -> TaskExecutionPolicy.requireSupported(task(type, TaskExecutionMode.LIVE)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsIsolatedAndFutureOrMigrationOnlyLineages() {
        assertThatThrownBy(() -> TaskExecutionPolicy.requireSupported(
                task(TaskLineageType.REPLAY, TaskExecutionMode.ISOLATED)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TaskExecutionPolicy.requireSupported(
                task(TaskLineageType.EXPERIMENT, TaskExecutionMode.LIVE)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TaskExecutionPolicy.requireSupported(
                task(TaskLineageType.LEGACY_UNKNOWN, TaskExecutionMode.LIVE)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMissingOrUnknownSemantics() {
        assertThatThrownBy(() -> TaskExecutionPolicy.requireSupported(new TaskEntity()))
                .isInstanceOf(BusinessException.class);
        TaskEntity unknown = new TaskEntity();
        unknown.setLineageType("UNKNOWN");
        unknown.setExecutionMode(TaskExecutionMode.LIVE.name());
        assertThatThrownBy(() -> TaskExecutionPolicy.requireSupported(unknown))
                .isInstanceOf(BusinessException.class);
    }

    private TaskEntity task(TaskLineageType lineageType, TaskExecutionMode executionMode) {
        TaskEntity task = new TaskEntity();
        task.setLineageType(lineageType.name());
        task.setExecutionMode(executionMode.name());
        return task;
    }
}
