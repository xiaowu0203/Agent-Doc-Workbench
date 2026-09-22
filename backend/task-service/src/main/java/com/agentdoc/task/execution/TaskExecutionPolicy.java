package com.agentdoc.task.execution;

import com.agentdoc.task.pojo.entity.TaskEntity;

/** 任务执行语义当前可用范围的统一校验入口。 */
public final class TaskExecutionPolicy {

    private TaskExecutionPolicy() {
    }

    /**
     * 同时校验永久合法矩阵和当前版本开放范围。
     *
     * @param task 待创建或调度的任务
     */
    public static void requireSupported(TaskEntity task) {
        TaskExecutionAvailability.requireAvailable(task);
    }
}
