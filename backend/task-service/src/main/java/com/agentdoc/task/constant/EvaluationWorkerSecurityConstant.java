package com.agentdoc.task.constant;

import java.util.Set;

/** 仅这些入口由WorkerCapability专用验证器处理。 */
public final class EvaluationWorkerSecurityConstant {
    public static final Set<String> PATHS = Set.of(
            "/api/task/internal/tasks/evaluation/status/query",
            "/api/task/internal/tasks/evaluation/evidence/query",
            "/api/task/internal/tasks/evaluation/document-changes/query",
            "/api/task/internal/tasks/evaluation/cancel");
    private EvaluationWorkerSecurityConstant() { }
}
